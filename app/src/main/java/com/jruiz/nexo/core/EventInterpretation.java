package com.jruiz.nexo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representación semántica intermedia del texto de un evento.
 *
 * No sustituye todavía al contrato público de RuleClassifier. Sirve para que
 * título, ubicación, descripción y tiempo compartan las mismas evidencias.
 */
public final class EventInterpretation {
    public enum State { EXPLICIT, INFERRED, AMBIGUOUS, INVALID, MISSING }

    public static final class Field<T> {
        public final T value;
        public final State state;
        public final String evidence;
        public final int start;
        public final int end;

        private Field(T value, State state, String evidence, int start, int end) {
            this.value = value;
            this.state = state == null ? State.MISSING : state;
            this.evidence = evidence == null ? "" : evidence;
            this.start = start;
            this.end = end;
        }

        public static <T> Field<T> missing() {
            return new Field<>(null, State.MISSING, "", -1, -1);
        }

        public static <T> Field<T> explicit(T value, String evidence, int start, int end) {
            return new Field<>(value, State.EXPLICIT, evidence, start, end);
        }

        public boolean hasValue() {
            if (value == null) return false;
            return !(value instanceof String) || !((String) value).isBlank();
        }
    }

    public static final class Person {
        public final String name;
        public final String role;
        public final String evidence;
        public final int start;
        public final int end;

        public Person(String name, String role, String evidence, int start, int end) {
            this.name = name == null ? "" : name;
            this.role = role == null ? "person" : role;
            this.evidence = evidence == null ? "" : evidence;
            this.start = start;
            this.end = end;
        }
    }

    public static final class DocumentReference {
        public final String kind;
        public final String evidence;
        public final int start;
        public final int end;

        public DocumentReference(String kind, String evidence, int start, int end) {
            this.kind = kind == null ? "document" : kind;
            this.evidence = evidence == null ? "" : evidence;
            this.start = start;
            this.end = end;
        }
    }

    public final String originalText;
    public Field<String> activity = Field.missing();
    public Field<String> purpose = Field.missing();
    public Field<String> place = Field.missing();
    public final List<Person> people = new ArrayList<>();
    public final List<DocumentReference> documents = new ArrayList<>();

    public EventInterpretation(String originalText) {
        this.originalText = originalText == null ? "" : originalText;
    }

    public List<Person> peopleView() {
        return Collections.unmodifiableList(people);
    }

    public List<DocumentReference> documentsView() {
        return Collections.unmodifiableList(documents);
    }

    public String firstProfessional() {
        for (Person person : people) {
            if ("professional".equals(person.role) && !person.name.isBlank()) return person.name;
        }
        return "";
    }

    /** Candidato de título construido por unidades semánticas, sin truncar palabras. */
    public String titleCandidate() {
        String p = purpose.hasValue() ? clean((String) purpose.value) : "";
        String a = activity.hasValue() ? clean((String) activity.value) : "";
        if (!p.isBlank()) {
            String an = TemporalParser.norm(a);
            String pn = TemporalParser.norm(p);
            if (an.startsWith("reunion") && !pn.startsWith("reunion")) return capitalize("Reunión de " + p);
            return capitalize(p);
        }
        return capitalize(a);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String capitalize(String value) {
        String v = clean(value);
        return v.isBlank() ? "" : Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }
}
