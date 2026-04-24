package dev.zaxoosh.performancedoctor;

public record Diagnosis(Severity severity, String title, String detail, String recommendation) {
}
