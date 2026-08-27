# USE Web Backend

Eigenständiges Java/Spring-Boot-Backend für das neue webbasierte UML/OCL-System.

Dieses Repository ist kein Fork des originalen USE-Projekts und verwendet den alten USE-Core nicht als Runtime-Dependency. Das originale USE-Projekt dient nur als fachliche Referenz für UML/OCL-Konzepte, Syntax, Verhalten und spätere Testfälle.

## Technischer Stand

Dieser Stand entspricht Backend-Implementierungsschritt 1:

- Spring-Boot-Projekt mit Maven
- Java 21 als Zielversion
- REST-Basis mit Spring Web
- Bean Validation
- Test-Setup mit JUnit 5 und Spring Boot Test
- minimale Health-API unter `/api/v1/health`
- getrennte Profile für Default, `dev` und `test`

Backend-Implementierungsschritt 2 ergänzt die sichtbare Package- und Schichtenstruktur für API, Application, Domain, OCL, Validation, Persistence, Error Handling und Configuration. Noch nicht enthalten sind fachliche Domänenklassen, Projektverwaltung, OCL-Implementierung, Validation Service und Persistenzlogik. Diese folgen in späteren Backend-Schritten.

## Start

```bash
mvn spring-boot:run
```

Health Check:

```bash
curl http://localhost:8080/api/v1/health
```

Erwartete Antwort:

```json
{
  "status": "UP",
  "service": "use-web-backend"
}
```

## Tests

```bash
mvn test
```

Der aktuelle Basistest prüft den vorhandenen Health-Endpunkt als tatsächlich nutzbaren API-Use-Case. Reine Struktur- oder Policy-Tests werden erst ergänzt, wenn sie konkrete fachliche oder technische Regeln absichern.

## Architekturhinweis

Das Backend wird schrittweise in Richtung der geplanten Schichten erweitert:

- REST API Layer
- Application Services
- Domain Model
- OCL Engine mit Lexer, Parser, AST, Typechecker und Evaluator
- Validation Engine
- Persistence
- Error Handling

Die Package-Struktur ist vorbereitet unter `src/main/java/de/useweb/backend/`:

```text
api/
application/
domain/
ocl/
validation/
persistence/
error/
config/
```

Der MVP-Workflow bleibt:

```text
Dashboard -> Start Project -> Class Diagram -> Object Diagram -> Check Constraints
```
