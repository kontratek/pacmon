using Microsoft.VisualStudio.TestTools.UnitTesting;
using Pacmon.Core;

namespace Pacmon.VisualStudio.Tests;

[TestClass]
public sealed class VisualStudioModelTests
{
    [TestMethod]
    public void CoverageContextUsesCaseInsensitiveNugetNotes()
    {
        var dependencies = NugetManifest.ExtractDependencies(
            "<PackageVersion Include=\"Newtonsoft.Json\" />",
            "Directory.Packages.props");
        var notes = Notes.Parse(Notes.NewFile("NEWTONSOFT.JSON", "JSON serialization"));

        var context = new CoverageContext("Directory.Packages.props", "C:\\repo", "notes.md", dependencies, notes);

        Assert.AreEqual(1, context.Analysis.Documented.Count);
        Assert.AreEqual(0, context.Analysis.Undocumented.Count);
    }

    [TestMethod]
    public void DependencyRowsExposeCoverageAndScope()
    {
        var dependency = NugetManifest.ExtractDependencies(
            "<GlobalPackageReference Include=\"Nerdbank.GitVersioning\" />",
            "Directory.Packages.props")[0];

        var documented = new DependencyRow(dependency, true);
        var undocumented = new DependencyRow(dependency, false);

        Assert.IsTrue(documented.Documented);
        Assert.IsFalse(undocumented.Documented);
        Assert.AreEqual("globalPackageReference", documented.Scope);
    }

    [TestMethod]
    public void ToolWindowPreservesAgentExpansionOnlyForTheSamePackage()
    {
        Assert.IsTrue(ToolWindowState.PreserveAgentExpansion("Newtonsoft.Json", "newtonsoft.json", true));
        Assert.IsFalse(ToolWindowState.PreserveAgentExpansion("Newtonsoft.Json", "Serilog", true));
        Assert.IsFalse(ToolWindowState.PreserveAgentExpansion("Newtonsoft.Json", "Newtonsoft.Json", false));
    }

    [DataTestMethod]
    [DataRow("C:\\repo\\.pacmon\\nuget\\DEPENDENCY-NOTES.md")]
    [DataRow("C:/repo/.pacmon/nuget/DEPENDENCY-NOTES.md")]
    public void RecognizesOnlyNugetNotesPath(string path) => Assert.IsTrue(PacmonRuntime.IsNotesPath(path));

    [TestMethod]
    public void RejectsOtherEcosystemNotesPath() =>
        Assert.IsFalse(PacmonRuntime.IsNotesPath("C:/repo/.pacmon/cargo/DEPENDENCY-NOTES.md"));

}
