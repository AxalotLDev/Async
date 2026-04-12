package com.axalotl.async.common.mixin.utils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generates a tiny placeholder mixin class that targets a 3rd-party entity class.
 *
 * <p>The stub itself contains no logic — its only purpose is to make Mixin
 * pull the target class through our {@link SynchronisePlugin#postApply}, where
 * {@link SyncItemPickupTransformer} then performs the actual bytecode rewrite
 * based on the {@code @SyncItemPickup} annotations the mod author already wrote
 * on their own methods.</p>
 *
 * <p>Equivalent Java:</p>
 * <pre>{@code
 * @Mixin(targets = "com.example.MyMob")
 * public class SyncStub_<hash> { }
 * }</pre>
 */
final class SyncStubMixinGenerator {

    static final String STUB_PACKAGE_INTERNAL = "com/axalotl/async/common/mixin/generated";
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";

    private SyncStubMixinGenerator() {}

    /** @return the FQN (dot-separated) of the generated stub class. */
    static String stubClassName(String targetInternalName) {
        String safe = targetInternalName.replace('/', '_').replace('$', '_');
        return STUB_PACKAGE_INTERNAL.replace('/', '.') + ".SyncStub_" + safe;
    }

    static String stubClassInternalName(String targetInternalName) {
        return stubClassName(targetInternalName).replace('.', '/');
    }

    static byte[] generate(String targetInternalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String stubInternal = stubClassInternalName(targetInternalName);
        cw.visit(Opcodes.V21,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                stubInternal,
                null,
                "java/lang/Object",
                null);

        AnnotationVisitor mixinAnno = cw.visitAnnotation(MIXIN_ANNOTATION, true);
        AnnotationVisitor targets = mixinAnno.visitArray("targets");
        targets.visit(null, targetInternalName.replace('/', '.'));
        targets.visitEnd();
        mixinAnno.visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
