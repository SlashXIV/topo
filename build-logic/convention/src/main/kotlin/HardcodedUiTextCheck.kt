import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Echoue si un texte visible est code en dur dans un composable.
 *
 * Les textes vivent dans `strings.xml` : c'est la que s'ecrit un francais correct
 * (accents, pluriels, espaces insecables), et c'est le prealable a toute autre
 * langue. Toute l'interface a du y etre deplacee d'un coup parce que rien ne
 * l'empechait de glisser ; cette verification l'empeche.
 *
 * Detection volontairement SIMPLE, par motifs : un litteral contenant au moins
 * une lettre, passe a `Text(`, `showSnackbar(`, ou a un parametre nomme qui porte
 * du texte visible (`contentDescription`, `title`, `label`...). Les gabarits
 * `$valeur` et `${expression}` sont retires avant de chercher une lettre : un
 * texte purement numerique comme "${sent}/${total}" n'a rien a traduire.
 *
 * Un faux positif se leve par un commentaire `// texte-technique : <raison>` en
 * fin de ligne — la raison est obligatoire, pour qu'une exception se relise.
 */
@CacheableTask
abstract class HardcodedUiTextCheck : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Rapport ecrit meme en cas de succes : c'est lui qui rend la tache incrementale. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val findings = sources.files.sortedBy { it.path }.flatMap { file ->
            findHardcodedUiText(file.readLines()).map { (line, text) -> "${file.path}:$line  $text" }
        }
        report.get().asFile.writeText(findings.joinToString("\n"))
        if (findings.isNotEmpty()) {
            throw GradleException(
                "Texte visible code en dur (a deplacer dans strings.xml, ou a justifier par " +
                    "« // texte-technique : raison ») :\n" + findings.joinToString("\n"),
            )
        }
    }
}

private val VISIBLE_TEXT = Regex(
    """(?:\bText\(\s*|\bshowSnackbar\(\s*|\b(contentDescription|title|message|label|hint|""" +
        """actionLabel|confirmLabel|text|placeholder)\s*=\s*)"((?:[^"\\]|\\.)*)"""",
)
private val TEMPLATE = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")
private val OPT_OUT = Regex("""//\s*texte-technique\s*:\s*\S""")

/**
 * Appel d'animation Compose : son `label` nomme l'animation dans l'inspecteur
 * d'Android Studio et n'est jamais affiche. Cherche sur la ligne et les trois
 * precedentes, l'appel etant souvent reparti sur plusieurs lignes.
 */
private val ANIMATION_CALL = Regex("""\b(?:animate\w*|\w*Transition|AnimatedVisibility|AnimatedContent|Crossfade)\s*\(""")
private const val ANIMATION_LOOKBACK = 3

/** @return (numero de ligne, ligne) pour chaque texte visible code en dur. */
internal fun findHardcodedUiText(lines: List<String>): List<Pair<Int, String>> =
    lines.withIndex().mapNotNull { (index, line) ->
        val code = line.trimStart()
        if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@mapNotNull null
        if (OPT_OUT.containsMatchIn(line)) return@mapNotNull null
        val hardcoded = VISIBLE_TEXT.findAll(line).any { match ->
            val (name, text) = match.destructured
            TEMPLATE.replace(text, "").any(Char::isLetter) && !(name == "label" && inAnimationCall(lines, index))
        }
        if (hardcoded) index + 1 to code else null
    }

private fun inAnimationCall(lines: List<String>, index: Int): Boolean =
    (maxOf(0, index - ANIMATION_LOOKBACK)..index).any { ANIMATION_CALL.containsMatchIn(lines[it]) }
