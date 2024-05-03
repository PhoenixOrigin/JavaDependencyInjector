package net.phoenix;


import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

public class Main {
    public static void main(String[] args) {
        ValueStorer.storeValue(String.class, "Ashley");
        ByteBuddyAgent.install();
        ValueStorer.redefine("net.phoenix");
        testMethod("Hello", "World");
    }

    public static void testMethod(@InjectParameter String param1, @InjectParameter String param2) {
        System.out.println(param1 + " " + param2);
    }
}