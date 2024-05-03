package net.phoenix;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.ClassDefinition;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ValueStorer {

    private static HashMap<Class<?>, Object> storedValues = new HashMap<>();

    public static void redefine(String pack) {
        Set<String> classes = findClasses(pack);
        for (String classPath : classes) {
            try {
                Class clazz = Class.forName(pack + "." + classPath.replace(".class", ""));
                URL url = new URL(clazz.getProtectionDomain().getCodeSource().getLocation(),
                        pack.replace(".", "/") + "/" + classPath.replace(".", "/").replace("/class", ".class"));
                InputStream classStream = url.openStream();
                byte[] bytecode = classStream.readAllBytes();
                ClassReader classReader = new ClassReader(bytecode);
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, 0);
                for (MethodNode methodNode : classNode.methods) {
                    List<Integer> annotatedParameters = getAnnotatedParameters(methodNode);
                    if (annotatedParameters == null) {
                        continue;
                    }

                    InsnList ins = new InsnList();
                    Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
                    for(int i : annotatedParameters) {
                        String paramType = argumentTypes[i].getClassName();
                        ins.add(new LdcInsnNode(Type.getType("L" + paramType.replace('.', '/') + ";")));
                        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/phoenix/ValueStorer", "getValue", "(Ljava/lang/Class;)Ljava/lang/Object;", false));
                        ins.add(new TypeInsnNode(Opcodes.CHECKCAST, paramType.replace('.', '/')));
                        ins.add(new VarInsnNode(Opcodes.ASTORE, i));
                    }

                    methodNode.instructions.insert(ins);
                }

                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                classNode.accept(classWriter);
                ClassDefinition definition = new ClassDefinition(clazz, classWriter.toByteArray());
                RedefineClassAgent.redefineClasses(definition);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static Set<String> findClasses(String packageName) {
        InputStream stream = ClassLoader.getSystemClassLoader()
                .getResourceAsStream(packageName.replaceAll("[.]", "/"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        return reader.lines()
                .filter(line -> line.endsWith(".class"))
                .collect(Collectors.toSet());
    }

    public static List<Integer> getAnnotatedParameters(MethodNode methodNode) {
        List<AnnotationNode>[] visibleAnnotations = methodNode.visibleParameterAnnotations;
        List<AnnotationNode>[] invisibleAnnotations = methodNode.invisibleParameterAnnotations;
        List<Integer> annotatedParameters = new ArrayList<>();

        if(visibleAnnotations != null) {
            for(int i = 0; i < visibleAnnotations.length; i++) {
                if(visibleAnnotations[i] != null) {
                    if(visibleAnnotations[i].stream().anyMatch(a -> a.desc.equals("Lnet/phoenix/InjectParameter;"))) {
                        annotatedParameters.add(i);
                    }
                }
            }
        }

        if(invisibleAnnotations != null) {
            for (int i = 0; i < invisibleAnnotations.length; i++) {
                if (invisibleAnnotations[i] != null) {
                    if (invisibleAnnotations[i].stream().anyMatch(a -> a.desc.equals("Lnet/phoenix/InjectParameter;"))) {
                        annotatedParameters.add(i);
                    }
                }
            }
        }
        return annotatedParameters;
    }

    public static void storeValue(Class<?> clazz, Object value) {
        if(!clazz.equals(value.getClass()))
            throw new IllegalArgumentException("Class and value must be of the same type");
        storedValues.put(clazz, value);
    }

    public static Object getValue(Class<?> clazz) {
        return storedValues.get(clazz);
    }

}
