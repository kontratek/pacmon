package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NugetManifestTest {
    @Test
    fun `matches CSharp FSharp VB and central manifests only`() {
        assertEquals(ManifestKind.NUGET, ManifestRegistry.forPath("/repo/App.csproj")?.kind)
        assertEquals(ManifestKind.NUGET, ManifestRegistry.forPath("/repo/App.fsproj")?.kind)
        assertEquals(ManifestKind.NUGET, ManifestRegistry.forPath("/repo/App.vbproj")?.kind)
        assertEquals(ManifestKind.NUGET, ManifestRegistry.forPath("/repo/Directory.Packages.props")?.kind)
        assertTrue(NugetManifestAdapter.matchesPath("/repo/App.CSPROJ"))
        assertFalse(NugetManifestAdapter.matchesPath("/repo/Directory.Build.props"))
        assertFalse(NugetManifestAdapter.matchesPath("/repo/packages.config"))
    }

    @Test
    fun `extracts literal project package references with precise ranges`() {
        val text = """
            <Project Sdk="Microsoft.NET.Sdk" xmlns:x="urn:test">
              <ItemGroup Condition="'${'$'}(TargetFramework)' == 'net9.0'">
                <PackageReference Include="Newtonsoft.Json" Version="13.0.3" />
                <x:PackageReference
                  Include="Serilog.AspNetCore">
                  <Version>9.0.0</Version>
                </x:PackageReference>
                <!-- <PackageReference Include="Commented.Package" /> -->
                <PackageReference Update="Imported.Package" Version="2" />
                <PackageReference Include="Removed.Package" Remove="Removed.Package" />
                <PackageReference Include="${'$'}(DynamicPackage)" />
                <ProjectReference Include="../Shared/Shared.csproj" />
                <FrameworkReference Include="Microsoft.AspNetCore.App" />
                <PackageDownload Include="Tool.Package" Version="[1.0.0]" />
              </ItemGroup>
            </Project>
        """.trimIndent()

        val dependencies = NugetManifestAdapter.extractDependencies(text, "/repo/App.csproj")
        assertEquals(
            listOf("packageReference:Newtonsoft.Json", "packageReference:Serilog.AspNetCore"),
            dependencies.map { "${it.scope}:${it.noteKey}" },
        )
        dependencies.forEach { dependency ->
            assertEquals(
                dependency.displayName,
                text.substring(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length),
            )
            assertEquals("<", text.substring(dependency.iconRange.offset, dependency.iconRange.offset + 1))
        }
    }

    @Test
    fun `extracts central versions and global package references`() {
        val text = """
            <Project xmlns:n="urn:test">
              <ItemGroup>
                <PackageVersion Include="Newtonsoft.Json" Version="13.0.3" />
                <n:PackageVersion Update="Serilog" Version="4.2.0" Condition="'${'$'}(TargetFramework)' == 'net9.0'" />
                <GlobalPackageReference Include="Nerdbank.GitVersioning" Version="3.7.115" />
                <PackageVersion Include="Removed.Package" Remove="Removed.Package" />
                <PackageVersion Include="${'$'}(DynamicPackage)" Version="1" />
                <PackageDownload Include="Download.Only" Version="[1.0.0]" />
              </ItemGroup>
            </Project>
        """.trimIndent()

        assertEquals(
            listOf(
                "centralVersion:Newtonsoft.Json",
                "centralVersion:Serilog",
                "globalPackageReference:Nerdbank.GitVersioning",
            ),
            NugetManifestAdapter.extractDependencies(text, "Directory.Packages.props")
                .map { "${it.scope}:${it.noteKey}" },
        )
    }

    @Test
    fun `decodes entities and safely keeps declarations before malformed XML`() {
        val entity = "<Project><PackageReference Include=\"Entity&#46;Package\" /></Project>"
        val dependency = NugetManifestAdapter.extractDependencies(entity, "App.csproj").single()
        assertEquals("Entity.Package", dependency.noteKey)
        assertEquals(
            "Entity&#46;Package",
            entity.substring(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length),
        )

        val malformed = "<Project><PackageReference Include=\"Good.Package\" /><PackageReference Include="
        assertEquals(
            listOf("Good.Package"),
            NugetManifestAdapter.extractDependencies(malformed, "App.csproj").map { it.noteKey },
        )
        assertTrue(
            NugetManifestAdapter.extractDependencies(
                "<Project><PackageReference Include=\"Bad&#999999999;Package\" /></Project>",
                "App.csproj",
            ).isEmpty(),
        )
    }

    @Test
    fun `comments and CDATA do not create package declarations`() {
        val text = """
            <Project>
              <!-- <PackageReference Include="Commented.Package" /> -->
              <![CDATA[<PackageReference Include="Cdata.Package" />]]>
              <PackageReference Include="Real.Package" />
            </Project>
        """.trimIndent()
        assertEquals(
            listOf("Real.Package"),
            NugetManifestAdapter.extractDependencies(text, "App.csproj").map { it.noteKey },
        )
    }
}
