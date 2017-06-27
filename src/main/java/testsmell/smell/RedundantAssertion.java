package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import testsmell.ISmell;
import testsmell.MethodSmell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
If a test method contains an assert statement that explicitly returns a true or false, the method is marked as smelly
 */
public class RedundantAssertion implements ITestSmell {

    List<ISmell> smellList;

    @Override
    public List<ISmell> runAnalysis(CompilationUnit cu) {
        smellList = new ArrayList<>();

        RedundantAssertion.ClassVisitor classVisitor = new RedundantAssertion.ClassVisitor();
        classVisitor.visit(cu, null);

        return smellList;
    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private MethodDeclaration currentMethod = null;
        private int redundantCount = 0;
        ISmell methodSmell;
        Map<String, String> map;


        // examine all methods in the test class
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            //only analyze methods that either have a @test annotation (Junit 4) or the method name starts with 'test'
            if (n.getAnnotationByName("Test").isPresent() || n.getNameAsString().toLowerCase().startsWith("test")) {
                currentMethod = n;
                methodSmell = new MethodSmell(currentMethod.getNameAsString());
                super.visit(n, arg);

                methodSmell.setHasSmell(redundantCount >= 1);

                map = new HashMap<>();
                map.put("RedundantCount", String.valueOf(redundantCount));
                methodSmell.setSmellData(map);

                smellList.add(methodSmell);

                //reset values for next method
                currentMethod = null;
                redundantCount = 0;
            }
        }


        @Override
        public void visit(MethodCallExpr n, Void arg) {
            String argumentValue = null;

            super.visit(n, arg);
            if (currentMethod != null) {
                switch (n.getNameAsString()) {
                    case "assertTrue":
                    case "assertFalse":
                        if (n.getArguments().size() == 1 && n.getArgument(0) instanceof BooleanLiteralExpr) { // assertTrue(boolean condition) or assertFalse(boolean condition)
                            argumentValue = Boolean.toString(((BooleanLiteralExpr) n.getArgument(0)).getValue());
                        } else if (n.getArguments().size() == 2 && n.getArgument(1) instanceof BooleanLiteralExpr) { // assertTrue(java.lang.String message, boolean condition)  or assertFalse(java.lang.String message, boolean condition)
                            argumentValue = Boolean.toString(((BooleanLiteralExpr) n.getArgument(1)).getValue());
                        }

                        if (argumentValue != null && (argumentValue.toLowerCase().equals("true") || argumentValue.toLowerCase().equals("false"))) {
                            redundantCount++;
                        }
                        break;

                    case "assertNotNull":
                    case "assertNull":
                        if (n.getArguments().size() == 1 && n.getArgument(0) instanceof NullLiteralExpr) { // assertNotNull(java.lang.Object object) or assertNull(java.lang.Object object)
                            argumentValue = (((NullLiteralExpr) n.getArgument(0)).toString());
                        } else if (n.getArguments().size() == 2 && n.getArgument(1) instanceof NullLiteralExpr) { // assertNotNull(java.lang.String message, java.lang.Object object) or assertNull(java.lang.String message, java.lang.Object object)
                            argumentValue = (((NullLiteralExpr) n.getArgument(1)).toString());
                        }

                        if (argumentValue != null && (argumentValue.toLowerCase().equals("null"))) {
                            redundantCount++;
                        }
                        break;

                    default:
                        if (n.getNameAsString().startsWith("assert")) {
                            if (n.getArguments().size() == 2) { //e.g. assertArrayEquals(byte[] expecteds, byte[] actuals); assertEquals(long expected, long actual);
                                if (n.getArgument(0).equals(n.getArgument(1))) {
                                    redundantCount++;
                                }
                            }
                            if (n.getArguments().size() == 3) { //e.g. assertArrayEquals(java.lang.String message, byte[] expecteds, byte[] actuals); assertEquals(java.lang.String message, long expected, long actual)
                                if (n.getArgument(1).equals(n.getArgument(2))) {
                                    redundantCount++;
                                }
                            }
                        }
                        break;
                }
            }
        }

    }
}