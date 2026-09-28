using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace Pacmon.Core.Tests;

[TestClass]
public sealed class NotesTests
{
    [TestMethod]
    public void CreatesNugetV2FileAndMatchesCaseInsensitively()
    {
        var text = Notes.NewFile("Newtonsoft.Json", "JSON serialization", "- purpose: JSON");
        var model = Notes.Parse(text);

        Assert.AreEqual(Notes.FormatVersion, model.Frontmatter?.FormatVersion);
        Assert.AreEqual(Notes.Ecosystem, model.Frontmatter?.Ecosystem);
        Assert.AreEqual("Newtonsoft.Json", Notes.FindSection(model, "NEWTONSOFT.JSON")?.Name);
    }

    [TestMethod]
    public void UpsertPreservesDisplayNameAndGeneratedLayer()
    {
        var original = Notes.NewFile("Newtonsoft.Json", "Old", "- purpose: JSON")
            .TrimEnd() + "\n\n### Generated\n\nDo not edit\n";

        var updated = Notes.UpsertLayers(original, "newtonsoft.json", "New", "- purpose: serializer");
        var model = Notes.Parse(updated);
        var section = Notes.FindSection(model, "NEWTONSOFT.JSON")!;
        var layers = Notes.Layers(model, section);

        Assert.AreEqual("Newtonsoft.Json", section.Name);
        Assert.AreEqual("New", layers.Human);
        Assert.AreEqual("- purpose: serializer", layers.Agent);
        StringAssert.Contains(layers.Generated, "### Generated");
        StringAssert.Contains(layers.Generated, "Do not edit");
    }

    [TestMethod]
    public void CoverageTreatsEmptySectionsAsUndocumentedAndKeepsRemovedNotes()
    {
        var text = Notes.NewFile().TrimEnd() + "\n\n## Alpha\n\n## Gone\n\n### Agent notes\n\n- status: removed 2026-09 — replaced\n";
        var dependencies = NugetManifest.ExtractDependencies("<PackageReference Include=\"alpha\" />", "App.csproj");

        var analysis = Notes.Analyze(dependencies, Notes.Parse(text));

        Assert.AreEqual(0, analysis.Documented.Count);
        Assert.AreEqual(1, analysis.Undocumented.Count);
        Assert.AreEqual(0, analysis.Orphans.Count);
        Assert.AreEqual(1, analysis.Removed.Count);
    }

    [TestMethod]
    public void LintFindsFrontmatterDuplicatesAndAgentProblems()
    {
        const string text = """
            ---
            format: dependency-notes/2
            ecosystem: cargo
            lang: en
            ---
            # Dependency Notes

            ## Alpha

            ### Agent notes

            - purpos: thing
            - verify:

            ## alpha
            duplicate
            """;

        var findings = Notes.Lint(Notes.Parse(text), new[] { "Alpha" });

        CollectionAssert.IsSubsetOf(
            new[] { "wrongEcosystem", "duplicateSection", "unknownAgentKey", "emptyAgentValue" },
            findings.Select(finding => finding.Kind).ToArray());
    }

    [TestMethod]
    public void PreviewRespectsLayerPreference()
    {
        var layers = new SectionLayers("Human first\nSecond", "- purpose: Agent purpose", string.Empty);

        Assert.AreEqual("Human first", Notes.Preview(layers, InlineSource.HumanFirst));
        Assert.AreEqual("Agent purpose", Notes.Preview(layers, InlineSource.AiFirst));
        Assert.AreEqual("Human first", Notes.Preview(layers, InlineSource.HumanOnly));
        Assert.AreEqual("Agent purpose", Notes.Preview(layers, InlineSource.AiOnly));
    }

    [TestMethod]
    public void NormalizePreservesBomAndCrlfWithoutDoublingCarriageReturns()
    {
        var original = "\ufeff" + Notes.NewFile("Beta", "Second").Replace("\r\n", "\n").Replace("\n", "\r\n")
            + "\r\n## Alpha\r\n\r\nFirst\r\n";

        var normalized = Notes.Normalize(original);

        Assert.AreEqual('\ufeff', normalized[0]);
        Assert.IsFalse(normalized.Contains("\r\r\n"));
        Assert.IsTrue(normalized.IndexOf("## Alpha", StringComparison.Ordinal) < normalized.IndexOf("## Beta", StringComparison.Ordinal));
    }
}
