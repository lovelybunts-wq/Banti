package com.example.ui.screens

import com.example.data.model.QuestionAttemptRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class HtmlReaderTheme(val label: String, val cssBg: String?, val cssText: String?) {
    DEFAULT("Original", null, null),
    LIGHT("Light", "#F7F9FC", "#14243A"),
    DARK("Dark", "#0F172A", "#E2E8F0"),
    SEPIA("Sepia", "#FBF5E6", "#3E2C1C"),
}

@Serializable
data class DomQuestionSnapshot(
    val questionNumber: Int = 1,
    val questionText: String = "",
    val options: List<String> = emptyList(),
    val selectedOption: String = "",
    val correctOption: String = "",
    val explanation: String = "",
    val isCorrect: Boolean = false,
    val isAttempted: Boolean = false,
)

@Serializable
data class DomQuizExtractionPayload(
    val scoreObtained: Double? = null,
    val totalMarks: Double? = null,
    val summary: String = "",
    val questions: List<DomQuestionSnapshot> = emptyList(),
)

object HtmlTestJsHelper {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseDomPayload(rawJson: String): DomQuizExtractionPayload? =
        runCatching { json.decodeFromString<DomQuizExtractionPayload>(rawJson) }.getOrNull()

    fun computeMetrics(
        payload: DomQuizExtractionPayload?,
        fallbackObtained: Double?,
        fallbackTotal: Double?,
    ): ComputedTestMetrics {
        val qs = payload?.questions.orEmpty().map {
            QuestionAttemptRecord(
                questionNumber = it.questionNumber,
                questionText = it.questionText,
                options = it.options,
                selectedOption = it.selectedOption,
                correctOption = it.correctOption,
                explanation = it.explanation,
                isCorrect = it.isCorrect,
                isAttempted = it.isAttempted,
            )
        }
        val totalQ = qs.size
        val attempted = qs.count { it.isAttempted }
        val correct = qs.count { it.isCorrect }
        val wrong = qs.count { it.isAttempted && !it.isCorrect }
        val unattempted = (totalQ - attempted).coerceAtLeast(0)

        val obtained = fallbackObtained ?: payload?.scoreObtained ?: correct.toDouble()
        val total = (fallbackTotal ?: payload?.totalMarks ?: totalQ.toDouble()).coerceAtLeast(1.0)
        return ComputedTestMetrics(
            obtained = obtained,
            total = total,
            totalQuestions = if (totalQ > 0) totalQ else total.toInt(),
            attempted = attempted,
            correct = correct,
            wrong = wrong,
            unattempted = unattempted,
            breakdown = qs,
        )
    }

    fun buildThemeCssJs(theme: HtmlReaderTheme): String {
        val bg = theme.cssBg ?: return "(function(){ var s = document.getElementById('ltThemeStyle'); if(s) s.remove(); })();"
        val fg = theme.cssText ?: "#14243A"
        return """
            (function(){
              var id='ltThemeStyle';
              var s=document.getElementById(id) || document.createElement('style');
              s.id=id;
              s.innerHTML='html, body { background: $bg !important; color: $fg !important; } .card, .question, section { background-color: rgba(128,128,128,0.08) !important; color: $fg !important; } label { color: $fg !important; }';
              if(!s.parentNode) document.head.appendChild(s);
            })();
        """.trimIndent()
    }

    fun buildPracticeHighlightJs(enabled: Boolean): String = """
        (function(){
          var exps = document.querySelectorAll('.explanation, [data-explanation]');
          for (var i=0; i<exps.length; i++) {
            exps[i].style.display = ${if (enabled) "'block'" else "'none'"};
          }
        })();
    """.trimIndent()

    val INJECTED_CBT_OBSERVER_JS = """
        (function() {
          if (window.__ltCbtHooked) return;
          window.__ltCbtHooked = true;
          function cleanText(el) { return (el && el.innerText ? el.innerText : '').replace(/\s+/g, ' ').trim(); }
          window.__ltExtractQuizSnapshot = function() {
            var qNodes = document.querySelectorAll('[data-question], .card, .question');
            var questions = [];
            var groups = {};
            if (qNodes.length > 0) {
              qNodes.forEach(function(node, idx) {
                var inputs = node.querySelectorAll('input[type="radio"], input[type="checkbox"]');
                if (!inputs || inputs.length === 0) return;
                var qTextEl = node.querySelector('.q-text, h3, h4, p') || node;
                var qText = cleanText(qTextEl);
                var opts = [];
                var selText = '';
                var corrText = '';
                var isCorr = false;
                var isAtt = false;
                inputs.forEach(function(inp) {
                  var lbl = inp.closest('label') || inp.parentElement;
                  var optTxt = cleanText(lbl) || inp.value || '';
                  if (optTxt) opts.push(optTxt);
                  var marksRight = inp.getAttribute('data-correct') === 'true' || inp.dataset.correct === 'true' || inp.value === '1';
                  if (marksRight && !corrText) corrText = optTxt;
                  if (inp.checked) {
                    isAtt = true;
                    selText = optTxt;
                    if (marksRight) isCorr = true;
                  }
                });
                var expEl = node.querySelector('.explanation, [data-explanation]');
                var expText = expEl ? cleanText(expEl) : '';
                questions.push({
                  questionNumber: questions.length + 1,
                  questionText: qText,
                  options: opts,
                  selectedOption: selText,
                  correctOption: corrText,
                  explanation: expText,
                  isCorrect: isCorr,
                  isAttempted: isAtt
                });
              });
            }
            var bodyText = document.body ? document.body.innerText : '';
            var m = bodyText.match(/(?:your\s*score|total\s*score|final\s*score|marks\s*obtained|score|result|प्राप्तांक|कुल\s*अंक)\s*[:=\-]?\s*(\d+(?:\.\d+)?)\s*(?:\/|out of)\s*(\d+(?:\.\d+)?)/i);
            var got = m ? parseFloat(m[1]) : null;
            var tot = m ? parseFloat(m[2]) : null;
            var payload = JSON.stringify({
              scoreObtained: (got !== null && !isNaN(got)) ? got : null,
              totalMarks: (tot !== null && !isNaN(tot) && tot > 0) ? tot : null,
              summary: m ? m[0].trim() : '',
              questions: questions
            });
            if (window.LearnTalentBridge && window.LearnTalentBridge.onQuizSnapshot) {
              window.LearnTalentBridge.onQuizSnapshot(payload);
            }
            return payload;
          };
          document.addEventListener('change', function() {
            setTimeout(window.__ltExtractQuizSnapshot, 150);
          }, true);
          document.addEventListener('submit', function() {
            setTimeout(function() {
              window.__ltExtractQuizSnapshot();
              if (window.LearnTalentBridge && window.LearnTalentBridge.onHtmlSubmitTriggered) {
                window.LearnTalentBridge.onHtmlSubmitTriggered();
              }
            }, 350);
          }, true);
          document.addEventListener('click', function(e) {
            var el = e.target;
            while (el && el !== document.body) {
              var txt = (el.innerText || el.value || '').toLowerCase();
              if (el.tagName === 'BUTTON' || (el.tagName === 'INPUT' && (el.type === 'submit' || el.type === 'button'))) {
                if (txt.indexOf('submit') !== -1 || txt.indexOf('finish') !== -1 || txt.indexOf('सबमिट') !== -1) {
                  setTimeout(function() {
                    window.__ltExtractQuizSnapshot();
                    if (window.LearnTalentBridge && window.LearnTalentBridge.onHtmlSubmitTriggered) {
                      window.LearnTalentBridge.onHtmlSubmitTriggered();
                    }
                  }, 350);
                  break;
                }
              }
              el = el.parentElement;
            }
          }, true);
        })();
    """.trimIndent()
}

data class ComputedTestMetrics(
    val obtained: Double,
    val total: Double,
    val totalQuestions: Int,
    val attempted: Int,
    val correct: Int,
    val wrong: Int,
    val unattempted: Int,
    val breakdown: List<QuestionAttemptRecord>,
)
