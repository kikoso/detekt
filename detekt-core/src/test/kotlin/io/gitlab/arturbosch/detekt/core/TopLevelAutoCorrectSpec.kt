package io.gitlab.arturbosch.detekt.core

import io.github.detekt.test.utils.NullPrintStream
import io.github.detekt.test.utils.readResourceContent
import io.github.detekt.test.utils.resourceAsPath
import io.github.detekt.test.utils.resourceUrl
import io.github.detekt.tooling.api.spec.ProcessingSpec
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.FileProcessListener
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.core.tooling.AnalysisFacade
import io.gitlab.arturbosch.detekt.core.tooling.DefaultLifecycle
import io.gitlab.arturbosch.detekt.core.tooling.inputPathsToKtFiles
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TopLevelAutoCorrectSpec {

    @Nested
    inner class `autoCorrect_ false on top level` {

        @Test
        fun `should format the test file but not print the modified content to disc`() {
            val fileContentBeforeAutoCorrect = readResourceContent("cases/Test.kt")
            val fileUnderTest = resourceAsPath("cases/Test.kt")
            val spec = ProcessingSpec {
                project {
                    inputPaths = listOf(fileUnderTest)
                }
                config {
                    resources = listOf(resourceUrl("configs/rule-and-ruleset-autocorrect-true.yaml"))
                }
                rules {
                    autoCorrect = false // fixture
                }
                logging {
                    outputChannel = NullPrintStream()
                    errorChannel = NullPrintStream()
                }
            }

            val contentChangedListener = object : FileProcessListener {
                override fun onFinish(files: List<KtFile>, result: Detektion, bindingContext: BindingContext) {
                    assertThat(files).hasSize(1)
                    assertThat(files[0].text).isNotEqualToIgnoringWhitespace(fileContentBeforeAutoCorrect)
                }
            }

            AnalysisFacade(spec).runAnalysis {
                DefaultLifecycle(
                    mockk(),
                    it,
                    inputPathsToKtFiles,
                    processorsProvider = { listOf(contentChangedListener) },
                    ruleSetsProvider = { listOf(TopLevelAutoCorrectProvider()) }
                )
            }

            assertThat(readResourceContent("cases/Test.kt")).isEqualTo(fileContentBeforeAutoCorrect)
        }
    }
}

private class DeleteAnnotationsRule : Rule() {
    override val issue = Issue("test-rule", Severity.CodeSmell, "", Debt.FIVE_MINS)
    override fun visitAnnotation(annotation: KtAnnotation) {
        annotation.delete()
    }
}

private class TopLevelAutoCorrectProvider : RuleSetProvider {
    override val ruleSetId: String = "test-rule-set"
    override fun instance(config: Config) = RuleSet(ruleSetId, listOf(DeleteAnnotationsRule()))
}
