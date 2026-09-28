using System.Text;
using System.Text.RegularExpressions;

namespace Pacmon.Core;

public static class Notes
{
    public const string FormatVersion = "dependency-notes/2";
    public const string Ecosystem = "nuget";
    public const string FileName = "DEPENDENCY-NOTES.md";
    public const string Title = "# Dependency Notes";
    public const string AgentHeading = "### Agent notes";
    public const string GeneratedHeading = "### Generated";

    private static readonly Regex HeadingPattern = new("^##(?!#)\\s+(.+?)\\s*$", RegexOptions.Compiled);
    private static readonly Regex SubheadingPattern = new("^###(?!#)\\s+(.+?)\\s*$", RegexOptions.Compiled);
    private static readonly Regex FencePattern = new("^\\s{0,3}(`{3,}|~{3,})", RegexOptions.Compiled);
    private static readonly Regex FrontmatterPattern = new("^([A-Za-z][\\w-]*):\\s*(.*?)\\s*$", RegexOptions.Compiled);
    private static readonly Regex AgentFieldPattern = new(
        "^\\s*[-*]\\s+([a-z][a-z0-9_-]*)\\s*:\\s*(.*)$",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly HashSet<string> AgentFields = new(StringComparer.OrdinalIgnoreCase)
    {
        "purpose", "constraint", "verify", "log", "verified", "runtime", "exposure", "status", "note", "bump-with",
    };

    public static string NormalizeName(string value) => StripQuotes(value).Trim().ToLowerInvariant();

    public static NotesFileModel Parse(string input)
    {
        var hadBom = input.Length > 0 && input[0] == '\ufeff';
        var text = hadBom ? input.Substring(1) : input;
        var eol = text.Contains("\r\n") ? "\r\n" : "\n";
        var lines = Regex.Split(text, "\\r?\\n");
        var model = new NotesFileModel { Eol = eol, HadBom = hadBom, Lines = lines };
        var cursor = 0;

        if (lines.Length > 0 && lines[0] == "---")
        {
            for (var end = 1; end < lines.Length; end++)
            {
                if (lines[end] != "---") continue;
                string? format = null;
                string? language = null;
                string? ecosystem = null;
                for (var line = 1; line < end; line++)
                {
                    var match = FrontmatterPattern.Match(lines[line]);
                    if (!match.Success || match.Groups[2].Value.Length == 0) continue;
                    switch (match.Groups[1].Value)
                    {
                        case "format": format = match.Groups[2].Value; break;
                        case "lang": language = match.Groups[2].Value; break;
                        case "ecosystem": ecosystem = match.Groups[2].Value; break;
                    }
                }
                model.Frontmatter = new Frontmatter(0, end, format, language, ecosystem);
                cursor = end + 1;
                break;
            }
        }

        var inFence = false;
        for (var line = cursor; line < lines.Length; line++)
        {
            if (FencePattern.IsMatch(lines[line]))
            {
                inFence = !inFence;
                continue;
            }
            if (!inFence && lines[line].StartsWith("# ", StringComparison.Ordinal))
            {
                model.TitleLine = line;
                break;
            }
        }

        var headings = new List<(int Line, string Name)>();
        inFence = false;
        for (var line = cursor; line < lines.Length; line++)
        {
            if (FencePattern.IsMatch(lines[line]))
            {
                inFence = !inFence;
                continue;
            }
            if (inFence) continue;
            var match = HeadingPattern.Match(lines[line]);
            if (match.Success) headings.Add((line, match.Groups[1].Value.Trim()));
        }

        var firstByName = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        for (var index = 0; index < headings.Count; index++)
        {
            var heading = headings[index];
            var bodyEnd = index + 1 < headings.Count ? headings[index + 1].Line : lines.Length;
            int? agentHeading = null;
            int? generatedHeading = null;
            inFence = false;
            for (var line = heading.Line + 1; line < bodyEnd; line++)
            {
                if (FencePattern.IsMatch(lines[line]))
                {
                    inFence = !inFence;
                    continue;
                }
                if (inFence) continue;
                var subheading = SubheadingPattern.Match(lines[line]);
                if (!subheading.Success) continue;
                var key = subheading.Groups[1].Value.Trim();
                if (agentHeading is null && key.Equals("Agent notes", StringComparison.OrdinalIgnoreCase)) agentHeading = line;
                if (generatedHeading is null && key.Equals("Generated", StringComparison.OrdinalIgnoreCase)) generatedHeading = line;
            }

            model.Sections.Add(new NoteSection(
                heading.Name,
                heading.Line,
                heading.Line + 1,
                bodyEnd,
                agentHeading,
                generatedHeading));

            var normalized = NormalizeName(heading.Name);
            if (firstByName.TryGetValue(normalized, out var firstLine))
                model.Problems.Add(new NotesProblem("duplicateSection", heading.Name, heading.Line, firstLine));
            else
                firstByName[normalized] = heading.Line;
        }

        return model;
    }

    public static NoteSection? FindSection(NotesFileModel model, string name)
    {
        var normalized = NormalizeName(name);
        return model.Sections.FirstOrDefault(section => NormalizeName(section.Name) == normalized);
    }

    public static SectionLayers Layers(NotesFileModel model, NoteSection section)
    {
        var humanEnd = section.AgentHeadingLine ?? section.GeneratedHeadingLine ?? section.BodyEnd;
        var human = Slice(model, section.BodyStart, humanEnd);
        var agent = section.AgentHeadingLine.HasValue
            ? Slice(model, section.AgentHeadingLine.Value + 1, section.GeneratedHeadingLine ?? section.BodyEnd)
            : string.Empty;
        var generated = section.GeneratedHeadingLine.HasValue
            ? Slice(model, section.GeneratedHeadingLine.Value, section.BodyEnd)
            : string.Empty;
        return new SectionLayers(human, agent, generated);
    }

    public static string Preview(SectionLayers layers, InlineSource source = InlineSource.HumanFirst, int maxLength = 90)
    {
        var human = layers.Human.Split('\n').Select(line => line.Trim()).FirstOrDefault(line => line.Length > 0) ?? string.Empty;
        human = Regex.Replace(human, "^[-*]\\s+", string.Empty);
        var purpose = ParseAgentFields(layers.Agent)
            .FirstOrDefault(field => field.Key.Equals("purpose", StringComparison.OrdinalIgnoreCase)).Value ?? string.Empty;
        var text = source switch
        {
            InlineSource.HumanFirst => human.Length > 0 ? human : purpose,
            InlineSource.AiFirst => purpose.Length > 0 ? purpose : human,
            InlineSource.HumanOnly => human,
            InlineSource.AiOnly => purpose,
            _ => human,
        };
        if (text.Length == 0) return "note";
        return text.Length > maxLength ? text.Substring(0, maxLength - 1) + "…" : text;
    }

    public static string NewFile(string? packageName = null, string? human = null, string? agent = null)
    {
        var builder = new StringBuilder();
        builder.AppendLine("---");
        builder.AppendLine($"format: {FormatVersion}");
        builder.AppendLine($"ecosystem: {Ecosystem}");
        builder.AppendLine("lang: en");
        builder.AppendLine("---");
        builder.AppendLine();
        builder.AppendLine("<!-- Each \"## name\" below is a package from the nuget dependency manifest.");
        builder.AppendLine("     People write directly below it; AI agents write under \"### Agent notes\". -->");
        builder.AppendLine();
        builder.AppendLine(Title);
        if (!string.IsNullOrWhiteSpace(packageName))
        {
            builder.AppendLine();
            AppendSection(builder, packageName!, human ?? string.Empty, agent ?? string.Empty, string.Empty);
        }
        return builder.ToString();
    }

    public static string UpsertLayers(string input, string packageName, string human, string agent)
    {
        var model = Parse(input);
        var section = FindSection(model, packageName);
        var eol = model.Eol;
        var replacement = BuildSection(
            section?.Name ?? packageName,
            human.Trim(),
            agent.Trim(),
            section is null ? string.Empty : Layers(model, section).Generated,
            eol);

        if (section is null)
        {
            var baseText = input.TrimEnd('\r', '\n');
            if (baseText.Length == 0) baseText = NewFile().TrimEnd('\r', '\n');
            return baseText + eol + eol + replacement + eol;
        }

        var lines = model.Lines.ToList();
        lines.RemoveRange(section.HeadingLine, section.BodyEnd - section.HeadingLine);
        lines.InsertRange(section.HeadingLine, Regex.Split(replacement, "\\r?\\n"));
        var output = string.Join(eol, lines).TrimEnd('\r', '\n') + eol;
        return model.HadBom ? "\ufeff" + output : output;
    }

    public static string Normalize(string input)
    {
        var model = Parse(input);
        var sections = model.Sections
            .GroupBy(section => NormalizeName(section.Name))
            .Select(group => group.First())
            .OrderBy(section => NormalizeName(section.Name), StringComparer.Ordinal)
            .ToArray();
        var builder = new StringBuilder(NewFile().TrimEnd('\r', '\n'));
        foreach (var section in sections)
        {
            var layers = Layers(model, section);
            builder.AppendLine();
            builder.AppendLine();
            builder.Append(BuildSection(section.Name, layers.Human, layers.Agent, layers.Generated, "\n"));
        }
        builder.AppendLine();
        var normalized = builder.ToString().Replace("\r\n", "\n").Replace("\n", model.Eol);
        return model.HadBom ? "\ufeff" + normalized : normalized;
    }

    public static NotesAnalysis Analyze(IEnumerable<DependencyEntry> dependencies, NotesFileModel? notes)
    {
        var deps = dependencies.ToArray();
        var sections = new Dictionary<string, NoteSection>(StringComparer.OrdinalIgnoreCase);
        var depNames = new HashSet<string>(deps.Select(dependency => dependency.NoteKey), StringComparer.OrdinalIgnoreCase);
        var orphans = new List<NoteSection>();
        var removed = new List<NoteSection>();
        foreach (var section in notes?.Sections ?? Enumerable.Empty<NoteSection>())
        {
            if (!sections.ContainsKey(section.Name)) sections[section.Name] = section;
            if (!depNames.Contains(section.Name))
            {
                if (notes is not null && IsRemoved(Layers(notes, section).Agent)) removed.Add(section);
                else orphans.Add(section);
            }
        }

        var documented = new List<DependencyEntry>();
        var undocumented = new List<DependencyEntry>();
        var byDependency = new Dictionary<string, NoteSection>(StringComparer.OrdinalIgnoreCase);
        foreach (var dependency in deps)
        {
            if (sections.TryGetValue(dependency.NoteKey, out var section)
                && notes is not null
                && !IsEmpty(Layers(notes, section)))
            {
                documented.Add(dependency);
                byDependency[dependency.NoteKey] = section;
            }
            else undocumented.Add(dependency);
        }
        return new NotesAnalysis(documented, undocumented, orphans, removed, byDependency);
    }

    public static IReadOnlyList<LintFinding> Lint(NotesFileModel model, IEnumerable<string> dependencyNames)
    {
        var findings = new List<LintFinding>();
        var deps = new HashSet<string>(dependencyNames, StringComparer.OrdinalIgnoreCase);
        if (model.Frontmatter is null) findings.Add(new LintFinding("missingFrontmatter", 0, "Add dependency-notes/2 frontmatter."));
        else
        {
            if (model.Frontmatter.FormatVersion != FormatVersion)
                findings.Add(new LintFinding("unknownFormat", model.Frontmatter.StartLine, $"Expected format: {FormatVersion}."));
            if (!string.Equals(model.Frontmatter.Ecosystem, Ecosystem, StringComparison.Ordinal))
                findings.Add(new LintFinding("wrongEcosystem", model.Frontmatter.StartLine, $"Expected ecosystem: {Ecosystem}."));
        }
        if (model.TitleLine is null) findings.Add(new LintFinding("missingTitle", 0, $"Add {Title}."));
        else if (model.Lines[model.TitleLine.Value] != Title)
            findings.Add(new LintFinding("wrongTitle", model.TitleLine.Value, $"Use {Title}."));
        foreach (var problem in model.Problems)
            findings.Add(new LintFinding(problem.Kind, problem.Line, $"Duplicate section for {problem.Name}."));

        foreach (var section in model.Sections)
        {
            if (!section.AgentHeadingLine.HasValue) continue;
            for (var line = section.AgentHeadingLine.Value + 1;
                 line < (section.GeneratedHeadingLine ?? section.BodyEnd);
                 line++)
            {
                var match = AgentFieldPattern.Match(model.Lines[line]);
                if (!match.Success || match.Groups[2].Value.StartsWith("//", StringComparison.Ordinal)) continue;
                var key = match.Groups[1].Value;
                var value = match.Groups[2].Value.Trim();
                var keyStart = model.Lines[line].IndexOf(key, StringComparison.Ordinal);
                if (!AgentFields.Contains(key))
                    findings.Add(new LintFinding("unknownAgentKey", line, $"Unknown agent field: {key}.", keyStart, keyStart + key.Length));
                else if (value.Length == 0 || Regex.IsMatch(value, "^[-—–]*$"))
                    findings.Add(new LintFinding("emptyAgentValue", line, $"Agent field {key} has no value.", keyStart, keyStart + key.Length));
                else if (key.Equals("status", StringComparison.OrdinalIgnoreCase)
                         && value.StartsWith("removed", StringComparison.OrdinalIgnoreCase)
                         && deps.Contains(section.Name))
                    findings.Add(new LintFinding("removedButPresent", line, $"{section.Name} is still declared.", keyStart, keyStart + key.Length));
            }
        }
        return findings;
    }

    private static List<(string Key, string Value)> ParseAgentFields(string agent)
    {
        var result = new List<(string Key, string Value)>();
        foreach (var line in Regex.Split(agent, "\\r?\\n"))
        {
            var match = AgentFieldPattern.Match(line);
            if (match.Success) result.Add((match.Groups[1].Value.ToLowerInvariant(), match.Groups[2].Value.Trim()));
        }
        return result;
    }

    private static bool IsEmpty(SectionLayers layers) =>
        layers.Human.Length == 0 && layers.Agent.Length == 0 && layers.Generated.Length == 0;

    private static bool IsRemoved(string agent) => ParseAgentFields(agent)
        .Any(field => field.Key == "status" && field.Value.StartsWith("removed", StringComparison.OrdinalIgnoreCase));

    private static string Slice(NotesFileModel model, int start, int end)
    {
        while (start < end && string.IsNullOrWhiteSpace(model.Lines[start])) start++;
        while (end > start && string.IsNullOrWhiteSpace(model.Lines[end - 1])) end--;
        return string.Join("\n", model.Lines.Skip(start).Take(end - start));
    }

    private static string StripQuotes(string value)
    {
        var trimmed = value.Trim();
        if (trimmed.Length >= 2
            && ((trimmed[0] == '`' && trimmed[trimmed.Length - 1] == '`')
                || (trimmed[0] == '"' && trimmed[trimmed.Length - 1] == '"')
                || (trimmed[0] == '\'' && trimmed[trimmed.Length - 1] == '\'')))
            return trimmed.Substring(1, trimmed.Length - 2).Trim();
        return trimmed;
    }

    private static string BuildSection(string name, string human, string agent, string generated, string eol)
    {
        var builder = new StringBuilder();
        builder.Append("## ").Append(name.Trim());
        if (human.Length > 0) builder.Append(eol).Append(eol).Append(human.Replace("\r\n", "\n").Replace("\n", eol));
        if (agent.Length > 0) builder.Append(eol).Append(eol).Append(AgentHeading).Append(eol).Append(eol)
            .Append(agent.Replace("\r\n", "\n").Replace("\n", eol));
        if (generated.Length > 0) builder.Append(eol).Append(eol)
            .Append(generated.Replace("\r\n", "\n").Replace("\n", eol));
        return builder.ToString();
    }

    private static void AppendSection(StringBuilder builder, string name, string human, string agent, string generated) =>
        builder.AppendLine(BuildSection(name, human.Trim(), agent.Trim(), generated.Trim(), "\n"));
}
