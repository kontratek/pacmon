import { describe, expect, it } from 'vitest';
import { analyze } from '../../core/analyze';
import { dependencyAtOffset, manifestAdapterForPath } from '../../core/manifest';
import { parseNotes } from '../../core/parseNotes';
import { extractRubyDependencies, uniqueRubyDependencies } from '../../core/ruby-manifest';

describe('Ruby manifest matching', () => {
  it('matches Bundler and RubyGems manifests but not lockfiles', () => {
    expect(manifestAdapterForPath('/repo/Gemfile')?.kind).toBe('ruby');
    expect(manifestAdapterForPath('/repo/gems.rb')?.kind).toBe('ruby');
    expect(manifestAdapterForPath('/repo/example.gemspec')?.kind).toBe('ruby');
    expect(manifestAdapterForPath('/repo/Gemfile.lock')).toBeUndefined();
    expect(manifestAdapterForPath('/repo/gems.locked')).toBeUndefined();
  });
});

describe('Gemfile dependencies', () => {
  const gemfile = `source "https://rubygems.org"

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
  git "https://example.test/repo.git" do
    gem "private_gem", :groups => [:production, :test]
  end
end

path "components" do
  gem "local_component"
end
`;

  it('extracts literal calls with group and platform scopes', () => {
    expect(extractRubyDependencies(gemfile).map((dependency) => `${dependency.scope}:${dependency.noteKey}`)).toEqual([
      'default:rails',
      'default:puma',
      'development,test:rspec',
      'development,test@mri,windows:debug',
      'runtime@jruby:jruby-openssl',
      'production,test:private_gem',
      'default:local_component',
    ]);
  });

  it('returns exact clickable name and icon ranges', () => {
    const dependencies = extractRubyDependencies(gemfile);
    for (const dependency of dependencies) {
      expect(gemfile.slice(
        dependency.primaryRange.offset,
        dependency.primaryRange.offset + dependency.primaryRange.length,
      )).toBe(dependency.displayName);
      expect(gemfile.slice(
        dependency.iconRange.offset,
        dependency.iconRange.offset + dependency.iconRange.length,
      )).toBe('gem');
      expect(dependencyAtOffset(dependencies, dependency.sourceRanges[0]!.offset + 1)).toBe(dependency);
    }
  });

  it('ignores comments, literals, dynamic names, definitions and unsupported directives', () => {
    const text = String.raw`# gem "commented"
message = "gem \"inside_string\""
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
`;
    expect(extractRubyDependencies(text).map((dependency) => dependency.noteKey)).toEqual(['real']);
  });

  it('keeps declarations found before malformed input', () => {
    expect(extractRubyDependencies('gem "first"\ngem "unterminated\n')).toEqual([
      expect.objectContaining({ noteKey: 'first', scope: 'default' }),
    ]);
  });
});

describe('gemspec dependencies', () => {
  const gemspec = `Gem::Specification.new do |spec|
  spec.add_dependency "rack", ">= 3"
  spec.add_runtime_dependency("zeitwerk", "~> 2.6")
  spec.add_development_dependency 'rspec'
  other.add_dependency "wrong_receiver"

  def spec.dynamic_dependencies
    spec.add_dependency "inside_method"
  end
end

outside.add_dependency "outside_block"
`;

  it('extracts runtime and development dependencies from the specification receiver', () => {
    expect(extractRubyDependencies(gemspec, '/repo/example.gemspec').map((dependency) =>
      `${dependency.scope}:${dependency.noteKey}`)).toEqual([
      'runtime:rack',
      'runtime:zeitwerk',
      'development:rspec',
    ]);
  });

  it('anchors icons at the gemspec dependency method', () => {
    for (const dependency of extractRubyDependencies(gemspec, 'example.gemspec')) {
      expect(gemspec.slice(
        dependency.primaryRange.offset,
        dependency.primaryRange.offset + dependency.primaryRange.length,
      )).toBe(dependency.noteKey);
      expect(gemspec.slice(
        dependency.iconRange.offset,
        dependency.iconRange.offset + dependency.iconRange.length,
      )).toMatch(/^add_(?:runtime_|development_)?dependency$/);
    }
  });
});

describe('Ruby note identity', () => {
  it('is case-sensitive and keeps dash and underscore distinct', () => {
    const dependencies = extractRubyDependencies('gem "rack"\ngem "my_gem"\ngem "my-gem"\n');
    const notes = parseNotes('---\nformat: dependency-notes/2\necosystem: ruby\n---\n# Dependency Notes\n\n## Rack\n\nWrong case.\n\n## my_gem\n\nUnderscore.\n');
    const result = analyze(dependencies, notes);
    expect(result.documented.map((dependency) => dependency.noteKey)).toEqual(['my_gem']);
    expect(result.undocumented.map((dependency) => dependency.noteKey)).toEqual(['rack', 'my-gem']);
  });

  it('deduplicates aggregate views by exact gem name', () => {
    const dependencies = [
      ...extractRubyDependencies('gem "rack"\ngem "Rack"\n'),
      ...extractRubyDependencies('Gem::Specification.new do |s|\n s.add_dependency "rack"\nend\n', 'x.gemspec'),
    ];
    expect(uniqueRubyDependencies(dependencies).map((dependency) => dependency.noteKey)).toEqual(['rack', 'Rack']);
  });
});
