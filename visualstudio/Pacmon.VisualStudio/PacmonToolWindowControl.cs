using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Threading;
using Microsoft.VisualStudio.Shell;
using Pacmon.Core;

namespace Pacmon.VisualStudio;

internal sealed class DependencyRow
{
    public DependencyRow(DependencyEntry dependency, bool documented)
    {
        Dependency = dependency;
        Status = documented ? "●" : "○";
        Label = dependency.DisplayName;
        Scope = dependency.Scope;
    }

    public DependencyEntry Dependency { get; }
    public string Status { get; }
    public string Label { get; }
    public string Scope { get; }
}

public sealed class PacmonToolWindowControl : UserControl
{
    private readonly TextBlock coverage = new();
    private readonly ListView dependencies = new();
    private readonly TextBlock packageTitle = new();
    private readonly TextBlock packageScope = new();
    private readonly TextBox human = new();
    private readonly TextBox agent = new();
    private readonly TextBlock status = new();
    private readonly DispatcherTimer saveTimer;
    private readonly ComboBox decorations = new();
    private readonly ComboBox inlineSource = new();
    private readonly ComboBox noteEntry = new();
    private readonly ComboBox monorepo = new();
    private readonly CheckBox margin = new() { Content = "Package icon" };
    private readonly CheckBox navigation = new() { Content = "Ctrl+click" };
    private readonly CheckBox quickAction = new() { Content = "Quick Action" };
    private CoverageContext? context;
    private DependencyRow? selected;
    private bool loading;
    private bool optionsLoading;
    private bool subscribed;

    public PacmonToolWindowControl()
    {
        saveTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(650) };
        saveTimer.Tick += (_, _) => Run(async () =>
        {
            saveTimer.Stop();
            await SaveCurrentAsync();
        });
        Content = BuildUi();
        Loaded += (_, _) => Run(OnLoadedAsync);
        Unloaded += (_, _) =>
        {
            if (subscribed && PacmonRuntime.Current is { } runtime) runtime.Changed -= RuntimeChanged;
            subscribed = false;
            Run(SaveCurrentAsync);
        };
    }

    public async Task SetContextAsync(string? manifestPath, string? packageName)
    {
        await SaveCurrentAsync();
        var runtime = PacmonRuntime.Current;
        if (runtime is null) return;
        context = await runtime.CoverageAsync(manifestPath);
        RenderCoverage();
        if (context is null) return;
        var row = packageName is null
            ? dependencies.Items.OfType<DependencyRow>().FirstOrDefault()
            : dependencies.Items.OfType<DependencyRow>().FirstOrDefault(item =>
                string.Equals(item.Dependency.NoteKey, packageName, StringComparison.OrdinalIgnoreCase));
        if (row is not null) dependencies.SelectedItem = row;
    }

    private UIElement BuildUi()
    {
        var root = new DockPanel { Margin = new Thickness(10) };
        var header = new StackPanel { Orientation = Orientation.Vertical };
        var titleRow = new DockPanel();
        titleRow.Children.Add(new TextBlock
        {
            Text = "Pacmon",
            FontSize = 18,
            FontWeight = FontWeights.SemiBold,
            VerticalAlignment = VerticalAlignment.Center,
        });
        var refresh = new Button { Content = "Refresh", HorizontalAlignment = HorizontalAlignment.Right, Padding = new Thickness(9, 3, 9, 3) };
        refresh.Click += (_, _) => Run(() => SetContextAsync(context?.ManifestPath, selected?.Dependency.NoteKey));
        DockPanel.SetDock(refresh, Dock.Right);
        titleRow.Children.Add(refresh);
        header.Children.Add(titleRow);
        coverage.Margin = new Thickness(0, 4, 0, 8);
        header.Children.Add(coverage);
        header.Children.Add(BuildSettings());
        DockPanel.SetDock(header, Dock.Top);
        root.Children.Add(header);

        status.Margin = new Thickness(0, 7, 0, 0);
        DockPanel.SetDock(status, Dock.Bottom);
        root.Children.Add(status);

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0.38, GridUnitType.Star), MinWidth = 185 });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(0.62, GridUnitType.Star), MinWidth = 260 });
        dependencies.SelectionChanged += (_, _) => Run(() => SelectAsync(dependencies.SelectedItem as DependencyRow));
        dependencies.ItemTemplate = BuildDependencyTemplate();
        Grid.SetColumn(dependencies, 0);
        grid.Children.Add(dependencies);

        var editor = new Grid { Margin = new Thickness(12, 0, 0, 0) };
        editor.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        editor.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        editor.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        editor.RowDefinitions.Add(new RowDefinition { Height = new GridLength(0.72, GridUnitType.Star) });
        editor.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        var packageHeader = new StackPanel();
        packageTitle.FontSize = 15;
        packageTitle.FontWeight = FontWeights.SemiBold;
        packageScope.Opacity = 0.7;
        packageHeader.Children.Add(packageTitle);
        packageHeader.Children.Add(packageScope);
        Grid.SetRow(packageHeader, 0);
        editor.Children.Add(packageHeader);
        ConfigureEditor(human, "Human notes");
        Grid.SetRow(human, 1);
        editor.Children.Add(human);
        var agentLabel = new TextBlock { Text = "Agent notes", FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 9, 0, 3) };
        Grid.SetRow(agentLabel, 2);
        editor.Children.Add(agentLabel);
        ConfigureEditor(agent, "- purpose: ...");
        Grid.SetRow(agent, 3);
        editor.Children.Add(agent);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right };
        var open = new Button { Content = "Open Markdown", Margin = new Thickness(6), Padding = new Thickness(8, 3, 8, 3) };
        var save = new Button { Content = "Save", Margin = new Thickness(6), Padding = new Thickness(12, 3, 12, 3) };
        open.Click += (_, _) => Run(async () =>
        {
            if (context is not null && selected is not null)
                await PacmonRuntime.Current!.EnsureSectionAndOpenAsync(context.ManifestPath, selected.Dependency.NoteKey);
        });
        save.Click += (_, _) => Run(SaveCurrentAsync);
        buttons.Children.Add(open);
        buttons.Children.Add(save);
        Grid.SetRow(buttons, 4);
        editor.Children.Add(buttons);
        Grid.SetColumn(editor, 1);
        grid.Children.Add(editor);
        root.Children.Add(grid);
        return root;
    }

    private FrameworkElement BuildSettings()
    {
        var expander = new Expander { Header = "Settings", Margin = new Thickness(0, 0, 0, 8) };
        var panel = new WrapPanel { Margin = new Thickness(8, 5, 0, 5) };
        AddEnumSetting(panel, "Decorations", decorations, Enum.GetValues(typeof(DecorationMode)));
        AddEnumSetting(panel, "Inline source", inlineSource, Enum.GetValues(typeof(InlineSource)));
        AddEnumSetting(panel, "Note entry", noteEntry, Enum.GetValues(typeof(NoteEntryMode)));
        AddEnumSetting(panel, "Monorepo", monorepo, Enum.GetValues(typeof(MonorepoMode)));
        foreach (var checkBox in new[] { margin, navigation, quickAction })
        {
            checkBox.Margin = new Thickness(8, 22, 4, 0);
            checkBox.Checked += OptionsChanged;
            checkBox.Unchecked += OptionsChanged;
            panel.Children.Add(checkBox);
        }
        expander.Content = panel;
        return expander;
    }

    private static void AddEnumSetting(Panel parent, string label, ComboBox comboBox, Array values)
    {
        var group = new StackPanel { Margin = new Thickness(0, 0, 10, 4), Width = 130 };
        group.Children.Add(new TextBlock { Text = label, Margin = new Thickness(0, 0, 0, 2) });
        foreach (var value in values) comboBox.Items.Add(value);
        group.Children.Add(comboBox);
        parent.Children.Add(group);
    }

    private static DataTemplate BuildDependencyTemplate()
    {
        var template = new DataTemplate(typeof(DependencyRow));
        var panel = new FrameworkElementFactory(typeof(StackPanel));
        panel.SetValue(StackPanel.OrientationProperty, Orientation.Horizontal);
        var status = new FrameworkElementFactory(typeof(TextBlock));
        status.SetBinding(TextBlock.TextProperty, new Binding(nameof(DependencyRow.Status)));
        status.SetValue(FrameworkElement.MarginProperty, new Thickness(0, 0, 6, 0));
        panel.AppendChild(status);
        var label = new FrameworkElementFactory(typeof(TextBlock));
        label.SetBinding(TextBlock.TextProperty, new Binding(nameof(DependencyRow.Label)));
        panel.AppendChild(label);
        template.VisualTree = panel;
        return template;
    }

    private void ConfigureEditor(TextBox editor, string placeholder)
    {
        editor.AcceptsReturn = true;
        editor.AcceptsTab = true;
        editor.TextWrapping = TextWrapping.Wrap;
        editor.VerticalScrollBarVisibility = ScrollBarVisibility.Auto;
        editor.Margin = new Thickness(0, 4, 0, 0);
        editor.ToolTip = placeholder;
        editor.TextChanged += (_, _) =>
        {
            if (loading) return;
            status.Text = "Unsaved changes";
            saveTimer.Stop();
            saveTimer.Start();
        };
    }

    private async Task OnLoadedAsync()
    {
        var runtime = PacmonRuntime.Current;
        if (runtime is null) return;
        if (!subscribed)
        {
            runtime.Changed += RuntimeChanged;
            subscribed = true;
        }
        LoadOptions();
        decorations.SelectionChanged += OptionsChanged;
        inlineSource.SelectionChanged += OptionsChanged;
        noteEntry.SelectionChanged += OptionsChanged;
        monorepo.SelectionChanged += OptionsChanged;
        await SetContextAsync(null, null);
    }

    private void RuntimeChanged(object sender, EventArgs args) => Run(async () =>
    {
        if (!human.IsKeyboardFocusWithin && !agent.IsKeyboardFocusWithin)
            await SetContextAsync(context?.ManifestPath, selected?.Dependency.NoteKey);
    });

    private static void Run(Func<Task> action) => ThreadHelper.JoinableTaskFactory.Run(action);

    private void LoadOptions()
    {
        var options = PacmonRuntime.Current!.Options;
        optionsLoading = true;
        decorations.SelectedItem = options.Decorations;
        inlineSource.SelectedItem = options.InlineSource;
        noteEntry.SelectedItem = options.NoteEntry;
        monorepo.SelectedItem = options.Monorepo;
        margin.IsChecked = options.MarginIcon;
        navigation.IsChecked = options.Navigation;
        quickAction.IsChecked = options.QuickAction;
        optionsLoading = false;
    }

    private void OptionsChanged(object sender, RoutedEventArgs args)
    {
        if (optionsLoading || PacmonRuntime.Current is null) return;
        var options = PacmonRuntime.Current.Options;
        if (decorations.SelectedItem is DecorationMode decoration) options.Decorations = decoration;
        if (inlineSource.SelectedItem is InlineSource source) options.InlineSource = source;
        if (noteEntry.SelectedItem is NoteEntryMode entry) options.NoteEntry = entry;
        if (monorepo.SelectedItem is MonorepoMode mode) options.Monorepo = mode;
        options.MarginIcon = margin.IsChecked == true;
        options.Navigation = navigation.IsChecked == true;
        options.QuickAction = quickAction.IsChecked == true;
        options.SaveSettingsToStorage();
        PacmonRuntime.Current.NotifyChanged();
    }

    private async Task SelectAsync(DependencyRow? row)
    {
        await SaveCurrentAsync();
        selected = row;
        loading = true;
        try
        {
            packageTitle.Text = row?.Label ?? "Select a dependency";
            packageScope.Text = row?.Scope ?? string.Empty;
            human.Text = string.Empty;
            agent.Text = string.Empty;
            if (row is null || context?.Notes is null) return;
            var section = Notes.FindSection(context.Notes, row.Dependency.NoteKey);
            if (section is null) return;
            var layers = Notes.Layers(context.Notes, section);
            human.Text = layers.Human;
            agent.Text = layers.Agent;
        }
        finally
        {
            loading = false;
            status.Text = string.Empty;
        }
    }

    private async Task SaveCurrentAsync()
    {
        saveTimer.Stop();
        var runtime = PacmonRuntime.Current;
        var currentContext = context;
        var current = selected;
        if (loading || runtime is null || currentContext is null || current is null || status.Text != "Unsaved changes") return;
        var humanText = human.Text;
        var agentText = agent.Text;
        status.Text = "Saving…";
        await runtime.SaveLayersAsync(currentContext.ManifestPath, current.Dependency.NoteKey, humanText, agentText);
        status.Text = "Saved";
    }

    private void RenderCoverage()
    {
        dependencies.Items.Clear();
        if (context is null)
        {
            coverage.Text = "Open a supported NuGet manifest to begin.";
            return;
        }
        var documented = new HashSet<string>(context.Analysis.Documented.Select(item => item.NoteKey), StringComparer.OrdinalIgnoreCase);
        coverage.Text = $"Documentation coverage: {documented.Count}/{context.Dependencies.Count} — {Path.GetFileName(context.ManifestPath)}";
        foreach (var dependency in context.Dependencies)
            dependencies.Items.Add(new DependencyRow(dependency, documented.Contains(dependency.NoteKey)));
    }
}
