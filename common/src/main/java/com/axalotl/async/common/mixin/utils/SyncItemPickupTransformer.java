package com.axalotl.async.common.mixin.utils;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Bytecode rewriter for methods marked with {@code @com.axalotl.async.api.annotation.SyncItemPickup}.
 *
 * <p>For each annotated method on the target class:</p>
 * <ol>
 *   <li>Renames the original method body to {@code &lt;name&gt;$async$body} (kept private).</li>
 *   <li>Replaces the original method with a wrapper that:
 *     <pre>
 *       if (item == null || item.isRemoved()) return;
 *       synchronized (item) {
 *           if (item.isRemoved()) return;
 *           this.&lt;name&gt;$async$body(args...);
 *       }
 *     </pre>
 *   </li>
 * </ol>
 *
 * <p>The lock target is the {@code ItemEntity} parameter itself, giving fine-grained
 * per-item serialization without needing a static lock field on the target class.</p>
 */
final class SyncItemPickupTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncItemPickupTransformer.class);

    private static final String ANNOTATION_DESC = "Lcom/axalotl/async/api/annotation/SyncItemPickup;";
    private static final String ITEM_ENTITY_INTERNAL = "net/minecraft/world/entity/item/ItemEntity";
    private static final String ENTITY_INTERNAL = "net/minecraft/world/entity/Entity";
    private static final String IS_REMOVED_DESC = "()Z";
    private static final String BODY_SUFFIX = "$async$body";

    private SyncItemPickupTransformer() {
    }

    static void apply(ClassNode targetClass) {
        // Snapshot to avoid CME — we add new methods during the loop.
        List<MethodNode> snapshot = new ArrayList<>(targetClass.methods);
        for (MethodNode method : snapshot) {
            if (!hasAnnotation(method)) continue;
            if ((method.access & Opcodes.ACC_ABSTRACT) != 0) continue;
            if (method.name.endsWith(BODY_SUFFIX)) continue;

            int itemArgIdx = findItemEntityArg(method.desc);
            if (itemArgIdx < 0) {
                LOGGER.warn("@SyncItemPickup on {}.{}{} skipped: no ItemEntity parameter found",
                        targetClass.name, method.name, method.desc);
                continue;
            }
            transform(targetClass, method, itemArgIdx);
            LOGGER.debug("Applied @SyncItemPickup to {}.{}{}", targetClass.name, method.name, method.desc);
        }
    }

    private static boolean hasAnnotation(MethodNode m) {
        if (m.visibleAnnotations != null) {
            for (AnnotationNode a : m.visibleAnnotations) {
                if (ANNOTATION_DESC.equals(a.desc)) return true;
            }
        }
        if (m.invisibleAnnotations != null) {
            for (AnnotationNode a : m.invisibleAnnotations) {
                if (ANNOTATION_DESC.equals(a.desc)) return true;
            }
        }
        return false;
    }

    private static int findItemEntityArg(String desc) {
        Type[] args = Type.getArgumentTypes(desc);
        for (int i = 0; i < args.length; i++) {
            if (args[i].getSort() == Type.OBJECT
                    && ITEM_ENTITY_INTERNAL.equals(args[i].getInternalName())) {
                return i;
            }
        }
        return -1;
    }

    private static void transform(ClassNode owner, MethodNode original, int itemArgIdx) {
        boolean isStatic = (original.access & Opcodes.ACC_STATIC) != 0;
        Type[] argTypes = Type.getArgumentTypes(original.desc);
        Type retType = Type.getReturnType(original.desc);

        // Local slot of the ItemEntity argument inside the method frame.
        int itemLocal = isStatic ? 0 : 1;
        for (int i = 0; i < itemArgIdx; i++) itemLocal += argTypes[i].getSize();

        // 1. Build renamed body method (clone of original, private).
        String bodyName = original.name + BODY_SUFFIX;
        MethodNode body = new MethodNode(Opcodes.ASM9,
                (original.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                bodyName, original.desc, original.signature,
                original.exceptions == null ? null : original.exceptions.toArray(new String[0]));
        body.instructions = original.instructions;
        body.tryCatchBlocks = original.tryCatchBlocks;
        body.localVariables = original.localVariables;
        body.maxStack = original.maxStack;
        body.maxLocals = original.maxLocals;
        body.visibleAnnotations = null;
        body.invisibleAnnotations = null;
        owner.methods.add(body);

        // 2. Replace original with the synchronized wrapper.
        original.instructions = new InsnList();
        original.tryCatchBlocks = new ArrayList<>();
        original.localVariables = null;

        InsnList code = original.instructions;
        LabelNode lAfterFirstCheck = new LabelNode();
        LabelNode lTryStart = new LabelNode();
        LabelNode lTryEnd = new LabelNode();
        LabelNode lHandler = new LabelNode();
        LabelNode lAfterRecheck = new LabelNode();
        LabelNode lExitNormal = new LabelNode();

        // Local slots for: lock copy, return-value temp.
        int firstFreeLocal = computeFirstFreeLocal(isStatic, argTypes);
        int lockLocal = firstFreeLocal;
        int retLocal = firstFreeLocal + 1; // only used for non-void

        // Pre-lock fast path: if (item == null || item.isRemoved()) return;
        code.add(new VarInsnNode(Opcodes.ALOAD, itemLocal));
        code.add(new JumpInsnNode(Opcodes.IFNULL, lExitNormal));
        code.add(new VarInsnNode(Opcodes.ALOAD, itemLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_INTERNAL, "isRemoved", IS_REMOVED_DESC, false));
        code.add(new JumpInsnNode(Opcodes.IFNE, lExitNormal));
        code.add(lAfterFirstCheck);

        // lock = item; monitorenter;
        code.add(new VarInsnNode(Opcodes.ALOAD, itemLocal));
        code.add(new VarInsnNode(Opcodes.ASTORE, lockLocal));
        code.add(new VarInsnNode(Opcodes.ALOAD, lockLocal));
        code.add(new InsnNode(Opcodes.MONITORENTER));

        // try {
        code.add(lTryStart);

        // re-check inside lock
        code.add(new VarInsnNode(Opcodes.ALOAD, itemLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY_INTERNAL, "isRemoved", IS_REMOVED_DESC, false));
        code.add(new JumpInsnNode(Opcodes.IFNE, lAfterRecheck));

        // call body(this, args...)
        if (!isStatic) code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int argLocal = isStatic ? 0 : 1;
        for (Type at : argTypes) {
            code.add(new VarInsnNode(at.getOpcode(Opcodes.ILOAD), argLocal));
            argLocal += at.getSize();
        }
        code.add(new MethodInsnNode(
                isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKESPECIAL,
                owner.name, bodyName, original.desc, false));

        if (retType.getSort() != Type.VOID) {
            code.add(new VarInsnNode(retType.getOpcode(Opcodes.ISTORE), retLocal));
        }

        code.add(lAfterRecheck);

        // monitorexit (normal path)
        code.add(new VarInsnNode(Opcodes.ALOAD, lockLocal));
        code.add(new InsnNode(Opcodes.MONITOREXIT));
        code.add(lTryEnd);

        // load return value (if any) and return
        if (retType.getSort() != Type.VOID) {
            code.add(new VarInsnNode(retType.getOpcode(Opcodes.ILOAD), retLocal));
        }
        code.add(returnInsn(retType));

        // catch-all handler: monitorexit + rethrow
        code.add(lHandler);
        code.add(new VarInsnNode(Opcodes.ALOAD, lockLocal));
        code.add(new InsnNode(Opcodes.MONITOREXIT));
        code.add(new InsnNode(Opcodes.ATHROW));

        // exit-normal (no body call): just return default value
        code.add(lExitNormal);
        code.add(defaultReturnInsns(retType));

        // try { ... } finally monitorexit
        original.tryCatchBlocks.add(new TryCatchBlockNode(lTryStart, lTryEnd, lHandler, null));
        // also cover the handler itself (in case monitorexit throws)
        original.tryCatchBlocks.add(new TryCatchBlockNode(lHandler, lExitNormal, lHandler, null));

        // Recompute frames; conservative.
        original.maxLocals = retLocal + retType.getSize();
        original.maxStack = Math.max(2, retType.getSize() + 1);
    }

    private static int computeFirstFreeLocal(boolean isStatic, Type[] argTypes) {
        int n = isStatic ? 0 : 1;
        for (Type t : argTypes) n += t.getSize();
        return n;
    }

    private static AbstractInsnNode returnInsn(Type retType) {
        return new InsnNode(retType.getOpcode(Opcodes.IRETURN));
    }

    private static InsnList defaultReturnInsns(Type retType) {
        InsnList l = new InsnList();
        switch (retType.getSort()) {
            case Type.VOID -> l.add(new InsnNode(Opcodes.RETURN));
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> {
                l.add(new InsnNode(Opcodes.ICONST_0));
                l.add(new InsnNode(Opcodes.IRETURN));
            }
            case Type.LONG -> {
                l.add(new InsnNode(Opcodes.LCONST_0));
                l.add(new InsnNode(Opcodes.LRETURN));
            }
            case Type.FLOAT -> {
                l.add(new InsnNode(Opcodes.FCONST_0));
                l.add(new InsnNode(Opcodes.FRETURN));
            }
            case Type.DOUBLE -> {
                l.add(new InsnNode(Opcodes.DCONST_0));
                l.add(new InsnNode(Opcodes.DRETURN));
            }
            default -> {
                l.add(new InsnNode(Opcodes.ACONST_NULL));
                l.add(new InsnNode(Opcodes.ARETURN));
            }
        }
        return l;
    }
}
