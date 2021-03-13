/*
 * Copyright 2013-2021 by Vegard IT GmbH (https://vegardit.com) and contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.vegardit.maven.plugin.depcheck;

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import net.sf.jstuff.core.Strings;

/**
 * Analyzes the byte code of the given class files for usage of other classes.
 *
 * @author Sebastian Thomschke, Vegard IT GmbH
 */
public abstract class AbstractClassAnalyzer {

   protected final int asmAPI;

   protected ClassVisitor classVisitor;
   protected final SignatureVisitor signatureVisitor;

   /**
    * @param asmAPI see {@link Opcodes}
    */
   protected AbstractClassAnalyzer(final int asmAPI) {
      this.asmAPI = asmAPI;

      signatureVisitor = new SignatureVisitor(asmAPI) {
         @Override
         public void visitClassType(final String internalClassName) {
            reportClassReference(internalClassName);
         }
      };
      initClassVisitor();
   }

   public int getAsmAPI() {
      return asmAPI;
   }

   private void initClassVisitor() {

      final AnnotationVisitor annotationVisitor = new AnnotationVisitor(asmAPI) {
         @Override
         public void visit(final String name, final Object value) {
            if (value instanceof org.objectweb.asm.Type) {
               final org.objectweb.asm.Type typeValue = (org.objectweb.asm.Type) value;
               reportClassReference(typeValue.getInternalName());
            }
         }

         @Override
         public AnnotationVisitor visitAnnotation(final String name, final String descriptor) {
            parseSignature(descriptor);
            return this;
         }
      };

      final FieldVisitor fieldVisitor = new FieldVisitor(asmAPI) {
         @Override
         public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }
      };

      final MethodVisitor methodVisitor = new MethodVisitor(asmAPI) {

         @Override
         public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
            parseSignature(descriptor);
            reportClassReference(owner);
         }

         @Override
         public AnnotationVisitor visitInsnAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public void visitLdcInsn(final Object cst) {
            if (cst instanceof Type) {
               reportClassReference(((Type) cst).getInternalName());
            }
         }

         @Override
         public void visitLocalVariable(final String name, final String descriptor, final String signature, final Label start, final Label end,
            final int index) {
            if ("this".equals(name) || "super".equals(name))
               return;
            parseSignature(signature == null ? descriptor : signature);
         }

         @Override
         public AnnotationVisitor visitLocalVariableAnnotation(final int typeRef, final TypePath typePath, final Label[] start, final Label[] end,
            final int[] index, final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor, final boolean itf) {
            parseSignature(descriptor);
            reportClassReference(owner);
         }

         @Override
         public void visitMultiANewArrayInsn(final String descriptor, final int dims) {
            parseSignature(descriptor);
         }

         @Override
         public AnnotationVisitor visitParameterAnnotation(final int parameter, final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public AnnotationVisitor visitTryCatchAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String internalClassName) {
            reportClassReference(internalClassName);
         }

         @Override
         public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public void visitTypeInsn(final int opcode, final String internalClassName) {
            reportClassReference(internalClassName);
         }
      };

      classVisitor = new ClassVisitor(asmAPI) {
         @Override
         public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
            reportClassReference(superName);
            if (interfaces != null) {
               for (final String internalClassName : interfaces) {
                  reportClassReference(internalClassName);
               }
            }
            if (signature != null) {
               parseSignature(signature);
            }
         }

         @Override
         public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }

         @Override
         public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
            parseSignature(signature == null ? descriptor : signature);
            if (value instanceof Type) {
               reportClassReference(((Type) value).getInternalName());
            }
            return fieldVisitor;
         }

         @Override
         public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
            parseSignature(signature == null ? descriptor : signature);
            if (exceptions != null) {
               for (final String internalClassName : exceptions) {
                  reportClassReference(internalClassName);
               }
            }
            return methodVisitor;
         }

         @Override
         public AnnotationVisitor visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
            parseSignature(descriptor);
            return annotationVisitor;
         }
      };
   }

   protected abstract void onClassName(String nameOfReferencedClass);

   /**
    * Called when a reference to another class was found.
    */
   protected abstract void onClassReference(String nameOfReferencedClass);

   private void parseSignature(final String signature) {
      new SignatureReader(signature).accept(signatureVisitor);
   }

   private void reportClassReference(final String internalClassName) {
      if (internalClassName == null)
         return;

      // e.g. "[B" or "[Ljava/lang/Object;"
      if (internalClassName.startsWith("[")) {
         parseSignature(internalClassName);
         return;
      }

      onClassReference(Strings.replaceChars(internalClassName, "/\\", "."));
   }

   public void scan(final InputStream classByteCode) throws IOException {
      final ClassReader cr = new ClassReader(classByteCode);
      onClassName(Strings.replaceChars(cr.getClassName(), "/\\", "."));
      cr.accept(classVisitor, ClassReader.SKIP_FRAMES);
   }
}
