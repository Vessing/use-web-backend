package de.useweb.backend.application.ocl;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;
import de.useweb.backend.api.dto.ocl.OclParseRequestDto;
import de.useweb.backend.api.dto.ocl.OclParseResponseDto;
import de.useweb.backend.api.dto.ocl.OclTokenDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.ast.AllInstancesExpression;
import de.useweb.backend.ocl.ast.AtPreExpression;
import de.useweb.backend.ocl.ast.CollectionItem;
import de.useweb.backend.ocl.ast.CollectionLiteralExpression;
import de.useweb.backend.ocl.ast.CollectionRangeItem;
import de.useweb.backend.ocl.ast.EnumLiteralExpression;
import de.useweb.backend.ocl.ast.LiteralExpression;
import de.useweb.backend.ocl.ast.IfExpression;
import de.useweb.backend.ocl.ast.LetExpression;
import de.useweb.backend.ocl.ast.IterateExpression;
import de.useweb.backend.ocl.ast.IteratorExpression;
import de.useweb.backend.ocl.ast.OclAstNode;
import de.useweb.backend.ocl.ast.OperationCallExpression;
import de.useweb.backend.ocl.ast.ParenthesizedExpression;
import de.useweb.backend.ocl.ast.PropertyAccessExpression;
import de.useweb.backend.ocl.ast.QualifiedPropertyAccessExpression;
import de.useweb.backend.ocl.ast.ResultExpression;
import de.useweb.backend.ocl.ast.SelfExpression;
import de.useweb.backend.ocl.ast.TupleExpression;
import de.useweb.backend.ocl.ast.TypeArgumentCallExpression;
import de.useweb.backend.ocl.ast.UnaryExpression;
import de.useweb.backend.ocl.ast.VariableExpression;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourceRange;
import de.useweb.backend.ocl.lexer.OclLexResult;
import de.useweb.backend.ocl.lexer.OclLexer;
import de.useweb.backend.ocl.lexer.OclToken;
import de.useweb.backend.ocl.parser.OclParseResult;
import de.useweb.backend.ocl.parser.OclParser;

@Service
public class OclParseService {

    private final OclLexer lexer;
    private final OclParser parser;
    private final OclDiagnosticMapper diagnosticMapper;

    public OclParseService() {
        this(new OclLexer(), new OclParser(), new OclDiagnosticMapper());
    }

    OclParseService(OclLexer lexer, OclParser parser, OclDiagnosticMapper diagnosticMapper) {
        this.lexer = lexer;
        this.parser = parser;
        this.diagnosticMapper = diagnosticMapper;
    }

    public OclParseResponseDto parse(OclParseRequestDto request) {
        String expression = request == null ? "" : request.expression();
        OclLexResult lexResult = lexer.tokenize(expression);
        OclParseResult parseResult = parser.parse(expression);
        return new OclParseResponseDto(
                parseResult.success(),
                diagnosticMapper.toDto(parseResult.diagnostics(), request == null ? null : request.sourceId(), request == null ? null : request.sourceKind(), request == null ? null : request.documentVersion()),
                lexResult.tokens().stream().map(this::toDto).toList(),
                parseResult.ast() == null ? null : toAstDto(parseResult.ast()));
    }

    private OclTokenDto toDto(OclToken token) {
        return new OclTokenDto(token.type().name(), token.text(), toDto(token.sourceRange()));
    }

    private SourceRangeDto toDto(SourceRange range) {
        return new SourceRangeDto(
                range.start().line(),
                range.start().column(),
                range.start().offset(),
                range.end().line(),
                range.end().column(),
                range.end().offset());
    }

    private Map<String, Object> toAstDto(OclAstNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", node.getClass().getSimpleName());
        result.put("sourceRange", toDto(node.sourceRange()));
        switch (node) {
            case AllInstancesExpression allInstances -> {
                result.put("typeName", allInstances.typeName());
                result.put("typeRange", toDto(allInstances.typeRange()));
                result.put("operationRange", toDto(allInstances.operationRange()));
            }
            case AtPreExpression atPre -> {
                result.put("expression", toAstDto(atPre.expression()));
                result.put("atPreRange", toDto(atPre.atPreRange()));
            }
            case SelfExpression ignored -> {
            }
            case EnumLiteralExpression enumLiteral -> {
                result.put("enumerationName", enumLiteral.enumerationName());
                result.put("literalName", enumLiteral.literalName());
                result.put("enumerationRange", toDto(enumLiteral.enumerationRange()));
                result.put("literalRange", toDto(enumLiteral.literalRange()));
            }
            case LiteralExpression literal -> {
                result.put("literalType", literal.literalType().name());
                result.put("value", literal.value());
            }
            case VariableExpression variable -> result.put("name", variable.name());
            case IfExpression ifExpression -> {
                result.put("conditionRange", toDto(ifExpression.conditionRange()));
                result.put("thenRange", toDto(ifExpression.thenRange()));
                result.put("elseRange", toDto(ifExpression.elseRange()));
                result.put("condition", toAstDto(ifExpression.condition()));
                result.put("thenExpression", toAstDto(ifExpression.thenExpression()));
                result.put("elseExpression", toAstDto(ifExpression.elseExpression()));
            }
            case LetExpression letExpression -> {
                result.put("variable", variableDto(letExpression.variable()));
                result.put("initializerRange", toDto(letExpression.initializerRange()));
                result.put("bodyRange", toDto(letExpression.bodyRange()));
                result.put("initializer", toAstDto(letExpression.initializer()));
                result.put("body", toAstDto(letExpression.body()));
            }
            case IterateExpression iterate -> {
                result.put("operationRange", toDto(iterate.operationRange()));
                result.put("initializerRange", toDto(iterate.initializerRange()));
                result.put("bodyRange", toDto(iterate.bodyRange()));
                result.put("source", toAstDto(iterate.source()));
                result.put("iterator", variableDto(iterate.iterators().getFirst()));
                result.put("iterators", iterate.iterators().stream().map(this::variableDto).toList());
                result.put("accumulator", variableDto(iterate.accumulator()));
                result.put("initializer", toAstDto(iterate.initializer()));
                result.put("body", toAstDto(iterate.body()));
            }
            case IteratorExpression iterator -> {
                result.put("iteratorKind", iterator.kind().oclName());
                result.put("operationRange", toDto(iterator.operationRange()));
                result.put("bodyRange", toDto(iterator.bodyRange()));
                result.put("source", toAstDto(iterator.source()));
                result.put("variables", iterator.variables().stream().map(this::variableDto).toList());
                result.put("body", toAstDto(iterator.body()));
            }
            case PropertyAccessExpression propertyAccess -> {
                result.put("propertyName", propertyAccess.propertyName());
                result.put("propertyRange", toDto(propertyAccess.propertyRange()));
                result.put("receiver", toAstDto(propertyAccess.receiver()));
            }
            case QualifiedPropertyAccessExpression qualified -> {
                result.put("propertyName", qualified.propertyName());
                result.put("propertyRange", toDto(qualified.propertyRange()));
                result.put("receiver", toAstDto(qualified.receiver()));
                result.put("qualifierArguments", qualified.qualifierArguments().stream().map(this::toAstDto).toList());
            }
            case ResultExpression ignored -> {
            }
            case OperationCallExpression operationCall -> {
                result.put("operation", operationCall.operationName());
                result.put("operationRange", toDto(operationCall.operationRange()));
                result.put("receiver", toAstDto(operationCall.receiver()));
                result.put("arguments", operationCall.arguments().stream().map(this::toAstDto).toList());
            }
            case TypeArgumentCallExpression typeCall -> {
                result.put("operation", typeCall.operationName());
                result.put("typeName", typeCall.typeName());
                result.put("operationRange", toDto(typeCall.operationRange()));
                result.put("typeRange", toDto(typeCall.typeRange()));
                result.put("receiver", toAstDto(typeCall.receiver()));
            }
            case TupleExpression tuple -> result.put("parts", tuple.parts().stream().map(part -> {
                Map<String, Object> partDto = new LinkedHashMap<>();
                partDto.put("name", part.name());
                partDto.put("nameRange", toDto(part.nameRange()));
                partDto.put("sourceRange", toDto(part.sourceRange()));
                partDto.put("value", toAstDto(part.value()));
                return partDto;
            }).toList());
            case BinaryExpression binary -> {
                result.put("operator", binary.operator().symbol());
                result.put("operatorRange", toDto(binary.operatorRange()));
                result.put("left", toAstDto(binary.left()));
                result.put("right", toAstDto(binary.right()));
            }
            case UnaryExpression unary -> {
                result.put("operator", unary.operator().symbol());
                result.put("expression", toAstDto(unary.expression()));
            }
            case CollectionLiteralExpression collectionLiteral -> {
                result.put("collectionKind", collectionLiteral.collectionKind().oclName());
                result.put("parts", collectionLiteral.parts().stream().map(part -> {
                    Map<String, Object> partDto = new LinkedHashMap<>();
                    partDto.put("sourceRange", toDto(part.sourceRange()));
                    if (part instanceof CollectionItem item) {
                        partDto.put("kind", "ITEM");
                        partDto.put("expression", toAstDto(item.expression()));
                    } else if (part instanceof CollectionRangeItem rangeItem) {
                        partDto.put("kind", "RANGE");
                        partDto.put("first", toAstDto(rangeItem.first()));
                        partDto.put("last", toAstDto(rangeItem.last()));
                    }
                    return partDto;
                }).toList());
            }
            case ParenthesizedExpression parenthesized -> result.put("expression", toAstDto(parenthesized.expression()));
        }
        return result;
    }

    private Map<String, Object> variableDto(de.useweb.backend.ocl.ast.VariableDeclaration variable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", variable.name());
        result.put("declaredType", variable.declaredTypeName());
        result.put("nameRange", toDto(variable.nameRange()));
        result.put("typeRange", variable.typeRange() == null ? null : toDto(variable.typeRange()));
        result.put("sourceRange", toDto(variable.sourceRange()));
        return result;
    }
}
