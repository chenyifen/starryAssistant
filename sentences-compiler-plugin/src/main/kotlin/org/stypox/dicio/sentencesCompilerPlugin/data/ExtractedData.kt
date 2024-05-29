package org.stypox.dicio.sentencesCompilerPlugin.data

import org.dicio.sentences_compiler.construct.SentenceConstructList
import java.io.File

data class ExtractedData(
    val skills: List<ExtractedSkill>,
    val languages: List<String>,
)

data class ExtractedSkill(
    val id: String,
    val specificity: Specificity,
    val sentenceDefinitions: List<SentenceDefinition>,
    val languageToSentences: Map<String, List<RawSentence>>,
)

data class ParsedData(
    val skills: List<ParsedSkill>,
    val languages: List<String>,
)

data class ParsedSkill(
    val id: String,
    val specificity: Specificity,
    val sentenceDefinitions: List<SentenceDefinition>,
    val languageToSentences: Map<String, List<ParsedSentence>>
)

data class RawSentence(
    val id: String,
    val file: File,
    val rawConstructs: String,
)

data class ParsedSentence(
    val id: String,
    val file: File,
    val rawConstructs: String,
    val constructs: SentenceConstructList,
)
