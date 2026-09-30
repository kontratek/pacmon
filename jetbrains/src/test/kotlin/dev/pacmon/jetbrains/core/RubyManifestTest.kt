package dev.pacmon.jetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RubyManifestTest {
    private val gemfile = """source "https://rubygems.org"

gem "rails", "~> 8.0"
gem("puma", ">= 6")

group :development, :test do
  gem "rspec"
  gem "debug", platforms: [:mri, :windows]
end

platforms(:jruby) do
  gem "jruby-openssl", group: :runtime
end

source "https://gems.example.test" do
  gem "private_gem", :groups => [:production, :test]
end
"""

    @Test
    fun `matches Ruby manifests but not lockfiles`() {
        assertEquals(ManifestKind.RUBY, ManifestRegistry.forPath("/repo/Gemfile")?.kind)
        assertEquals(ManifestKind.RUBY, ManifestRegistry.forPath("/repo/gems.rb")?.kind)
        assertEquals(ManifestKind.RUBY, ManifestRegistry.forPath("/repo/example.gemspec")?.kind)
        assertNull(ManifestRegistry.forPath("/repo/Gemfile.lock"))
    }

    @Test
    fun `reads Gemfile groups platforms and source blocks`() {
        assertEquals(
            listOf(
                "rails" to "default",
                "puma" to "default",
                "rspec" to "development,test",
                "debug" to "development,test@mri,windows",
                "jruby-openssl" to "runtime@jruby",
                "private_gem" to "production,test",
            ),
            RubyManifestAdapter.extractDependencies(gemfile).map { it.noteKey to it.scope },
        )
    }

    @Test
    fun `keeps exact clickable name and icon ranges`() {
        for (dependency in RubyManifestAdapter.extractDependencies(gemfile)) {
            assertEquals(
                dependency.displayName,
                gemfile.substring(dependency.primaryRange.offset, dependency.primaryRange.offset + dependency.primaryRange.length),
            )
            assertEquals(
                "gem",
                gemfile.substring(dependency.iconRange.offset, dependency.iconRange.offset + dependency.iconRange.length),
            )
        }
    }

    @Test
    fun `ignores non-code dynamic and definition declarations`() {
        val text = """# gem "commented"
message = "gem inside_string"
pattern = /gem "inside_regex"/
percent = %q{gem "inside_percent"}
document = <<~TEXT
  gem "inside_heredoc"
TEXT
=begin
gem "inside_block_comment"
=end
name = "dynamic"
gem name
gem "dynamic-#{ENV.fetch('TARGET')}"
eval_gemfile "other.gemfile"
gemspec

def dependencies
  gem "inside_method"
end

class Recipe
  gem "inside_class"
end

gem "real"
"""
        assertEquals(listOf("real"), RubyManifestAdapter.extractDependencies(text).map { it.noteKey })
    }

    @Test
    fun `reads only the active gemspec receiver`() {
        val text = """Gem::Specification.new do |spec|
  spec.add_dependency "rack", ">= 3"
  spec.add_runtime_dependency("zeitwerk", "~> 2.6")
  spec.add_development_dependency 'rspec'
  other.add_dependency "wrong_receiver"

  def spec.dynamic_dependencies
    spec.add_dependency "inside_method"
  end
end

outside.add_dependency "outside_block"
"""
        assertEquals(
            listOf("rack" to "runtime", "zeitwerk" to "runtime", "rspec" to "development"),
            RubyManifestAdapter.extractDependencies(text, "/repo/example.gemspec").map { it.noteKey to it.scope },
        )
    }

    @Test
    fun `keeps earlier declarations when the tail is malformed`() {
        val dependencies = RubyManifestAdapter.extractDependencies("gem \"first\"\ngem \"unterminated\n")
        assertEquals(listOf("first"), dependencies.map { it.noteKey })
    }

    @Test
    fun `deduplicates aggregate views with case-sensitive keys`() {
        val dependencies = RubyManifestAdapter.extractDependencies("gem \"rack\"\ngem \"Rack\"\n") +
            RubyManifestAdapter.extractDependencies(
                "Gem::Specification.new do |s|\n s.add_dependency \"rack\"\nend\n",
                "x.gemspec",
            )
        assertEquals(listOf("rack", "Rack"), RubyManifestAdapter.uniqueDependencies(dependencies).map { it.noteKey })
        assertTrue(RubyManifestAdapter.normalizeNoteKey("my_gem") != RubyManifestAdapter.normalizeNoteKey("my-gem"))
    }
}
