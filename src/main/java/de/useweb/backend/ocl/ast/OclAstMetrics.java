package de.useweb.backend.ocl.ast;

import java.util.ArrayDeque;

public final class OclAstMetrics {
    private OclAstMetrics() {
    }

    public static int maxDepth(OclAstNode root) {
        ArrayDeque<NodeDepth> pending = new ArrayDeque<>();
        pending.push(new NodeDepth(root, 1));
        int maximum = 0;
        while (!pending.isEmpty()) {
            NodeDepth current = pending.pop();
            maximum = Math.max(maximum, current.depth());
            int childDepth = current.depth() + 1;
            switch (current.node()) {
                case AtPreExpression node -> push(pending, node.expression(), childDepth);
                case BinaryExpression node -> {
                    push(pending, node.left(), childDepth);
                    push(pending, node.right(), childDepth);
                }
                case CollectionLiteralExpression node -> node.parts().forEach(part -> {
                    if (part instanceof CollectionItem item) push(pending, item.expression(), childDepth);
                    if (part instanceof CollectionRangeItem range) {
                        push(pending, range.first(), childDepth);
                        push(pending, range.last(), childDepth);
                    }
                });
                case IfExpression node -> {
                    push(pending, node.condition(), childDepth);
                    push(pending, node.thenExpression(), childDepth);
                    push(pending, node.elseExpression(), childDepth);
                }
                case IterateExpression node -> {
                    push(pending, node.source(), childDepth);
                    push(pending, node.initializer(), childDepth);
                    push(pending, node.body(), childDepth);
                }
                case IteratorExpression node -> {
                    push(pending, node.source(), childDepth);
                    push(pending, node.body(), childDepth);
                }
                case LetExpression node -> {
                    push(pending, node.initializer(), childDepth);
                    push(pending, node.body(), childDepth);
                }
                case OperationCallExpression node -> {
                    push(pending, node.receiver(), childDepth);
                    node.arguments().forEach(argument -> push(pending, argument, childDepth));
                }
                case ParenthesizedExpression node -> push(pending, node.expression(), childDepth);
                case PropertyAccessExpression node -> push(pending, node.receiver(), childDepth);
                case QualifiedPropertyAccessExpression node -> {
                    push(pending, node.receiver(), childDepth);
                    node.qualifierArguments().forEach(argument -> push(pending, argument, childDepth));
                }
                case TupleExpression node -> node.parts().forEach(part -> push(pending, part.value(), childDepth));
                case TypeArgumentCallExpression node -> push(pending, node.receiver(), childDepth);
                case UnaryExpression node -> push(pending, node.expression(), childDepth);
                case AllInstancesExpression ignored -> { }
                case EnumLiteralExpression ignored -> { }
                case LiteralExpression ignored -> { }
                case ResultExpression ignored -> { }
                case SelfExpression ignored -> { }
                case VariableExpression ignored -> { }
            }
        }
        return maximum;
    }

    private static void push(ArrayDeque<NodeDepth> pending, OclAstNode node, int depth) {
        if (node != null) pending.push(new NodeDepth(node, depth));
    }

    private record NodeDepth(OclAstNode node, int depth) {
    }
}
