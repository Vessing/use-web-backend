package de.useweb.backend.application.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.ocl.ast.AtPreExpression;
import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.ast.CollectionItem;
import de.useweb.backend.ocl.ast.CollectionLiteralExpression;
import de.useweb.backend.ocl.ast.CollectionRangeItem;
import de.useweb.backend.ocl.ast.IfExpression;
import de.useweb.backend.ocl.ast.IterateExpression;
import de.useweb.backend.ocl.ast.IteratorExpression;
import de.useweb.backend.ocl.ast.LetExpression;
import de.useweb.backend.ocl.ast.OclAstNode;
import de.useweb.backend.ocl.ast.OperationCallExpression;
import de.useweb.backend.ocl.ast.ParenthesizedExpression;
import de.useweb.backend.ocl.ast.PropertyAccessExpression;
import de.useweb.backend.ocl.ast.QualifiedPropertyAccessExpression;
import de.useweb.backend.ocl.ast.TupleExpression;
import de.useweb.backend.ocl.ast.TypeArgumentCallExpression;
import de.useweb.backend.ocl.ast.UnaryExpression;
import de.useweb.backend.ocl.diagnostics.SourceRange;

final class OclPropertyAccessFinder {
    private OclPropertyAccessFinder() { }

    static List<SourceRange> find(OclAstNode root, String propertyName) {
        if (root == null || propertyName == null || propertyName.isBlank()) return List.of();
        ArrayDeque<OclAstNode> pending = new ArrayDeque<>();
        List<SourceRange> ranges = new ArrayList<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            OclAstNode current = pending.pop();
            switch (current) {
                case PropertyAccessExpression node -> {
                    if (propertyName.equals(node.propertyName())) ranges.add(node.propertyRange());
                    push(pending, node.receiver());
                }
                case AtPreExpression node -> push(pending, node.expression());
                case BinaryExpression node -> {
                    push(pending, node.left());
                    push(pending, node.right());
                }
                case CollectionLiteralExpression node -> node.parts().forEach(part -> {
                    if (part instanceof CollectionItem item) push(pending, item.expression());
                    if (part instanceof CollectionRangeItem range) {
                        push(pending, range.first());
                        push(pending, range.last());
                    }
                });
                case IfExpression node -> {
                    push(pending, node.condition());
                    push(pending, node.thenExpression());
                    push(pending, node.elseExpression());
                }
                case IterateExpression node -> {
                    push(pending, node.source());
                    push(pending, node.initializer());
                    push(pending, node.body());
                }
                case IteratorExpression node -> {
                    push(pending, node.source());
                    push(pending, node.body());
                }
                case LetExpression node -> {
                    push(pending, node.initializer());
                    push(pending, node.body());
                }
                case OperationCallExpression node -> {
                    push(pending, node.receiver());
                    node.arguments().forEach(argument -> push(pending, argument));
                }
                case ParenthesizedExpression node -> push(pending, node.expression());
                case QualifiedPropertyAccessExpression node -> {
                    push(pending, node.receiver());
                    node.qualifierArguments().forEach(argument -> push(pending, argument));
                }
                case TupleExpression node -> node.parts().forEach(part -> push(pending, part.value()));
                case TypeArgumentCallExpression node -> push(pending, node.receiver());
                case UnaryExpression node -> push(pending, node.expression());
                default -> { }
            }
        }
        return List.copyOf(ranges);
    }

    private static void push(ArrayDeque<OclAstNode> pending, OclAstNode node) {
        if (node != null) pending.push(node);
    }
}
