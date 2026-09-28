using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;
using Microsoft.VisualStudio.Shell;

namespace Pacmon.VisualStudio;

internal sealed class PacmonMarkControl : Canvas
{
    public static readonly DependencyProperty DocumentedProperty = DependencyProperty.Register(
        nameof(Documented),
        typeof(bool),
        typeof(PacmonMarkControl),
        new FrameworkPropertyMetadata(false, (source, _) => ((PacmonMarkControl)source).Draw()));

    public PacmonMarkControl()
    {
        Width = 14;
        Height = 14;
        SnapsToDevicePixels = true;
        Loaded += (_, _) => Draw();
    }

    public bool Documented
    {
        get => (bool)GetValue(DocumentedProperty);
        set
        {
            SetValue(DocumentedProperty, value);
            Draw();
        }
    }

    private void Draw()
    {
        Children.Clear();
        Opacity = Documented ? 1 : 0.68;
        AddBar(3.5, 0, 10.5, 3.2);
        AddBar(0, 5.4, 10.5, 3.2);
        AddBar(0, 10.8, 10.5, 3.2);
    }

    private void AddBar(double left, double top, double width, double height)
    {
        var bar = new Rectangle
        {
            Width = width,
            Height = height,
            Fill = Documented ? null : Brushes.Transparent,
            StrokeThickness = Documented ? 0 : 1,
            SnapsToDevicePixels = true,
        };
        if (Documented)
            bar.SetResourceReference(Shape.FillProperty, VsBrushes.ToolWindowTextKey);
        else
            bar.SetResourceReference(Shape.StrokeProperty, VsBrushes.ToolWindowTextKey);
        SetLeft(bar, left);
        SetTop(bar, top);
        Children.Add(bar);
    }
}

internal sealed class MarkdownPresenter : StackPanel
{
    private static readonly Regex InlinePattern = new(
        "(`[^`]+`|\\*\\*[^*]+\\*\\*|\\[[^]]+\\]\\([^)]+\\))",
        RegexOptions.Compiled);

    public MarkdownPresenter()
    {
        Orientation = Orientation.Vertical;
    }

    public void SetMarkdown(string markdown, string emptyText)
    {
        Children.Clear();
        var lines = Regex.Split(markdown ?? string.Empty, "\\r?\\n");
        if (lines.All(string.IsNullOrWhiteSpace))
        {
            Children.Add(new TextBlock
            {
                Text = emptyText,
                Opacity = 0.68,
                TextWrapping = TextWrapping.Wrap,
                FontStyle = FontStyles.Italic,
            });
            return;
        }

        foreach (var rawLine in lines)
        {
            var line = rawLine.TrimEnd();
            if (line.Length == 0)
            {
                Children.Add(new Border { Height = 7 });
                continue;
            }

            var text = new TextBlock { TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 1, 0, 1) };
            var heading = Regex.Match(line, "^(#{1,3})\\s+(.+)$");
            if (heading.Success)
            {
                text.FontWeight = FontWeights.SemiBold;
                text.FontSize += 3 - heading.Groups[1].Value.Length;
                AppendInline(text, heading.Groups[2].Value);
            }
            else if (Regex.IsMatch(line, "^\\s*[-*]\\s+"))
            {
                text.Inlines.Add(new Run("•  ") { FontWeight = FontWeights.SemiBold });
                AppendInline(text, Regex.Replace(line, "^\\s*[-*]\\s+", string.Empty));
            }
            else
            {
                AppendInline(text, line);
            }
            Children.Add(text);
        }
    }

    private static void AppendInline(TextBlock target, string text)
    {
        var cursor = 0;
        foreach (Match match in InlinePattern.Matches(text))
        {
            if (match.Index > cursor) target.Inlines.Add(new Run(text.Substring(cursor, match.Index - cursor)));
            var value = match.Value;
            if (value.StartsWith("`", StringComparison.Ordinal))
            {
                target.Inlines.Add(new Run(value.Substring(1, value.Length - 2))
                {
                    FontFamily = new FontFamily("Consolas"),
                });
            }
            else if (value.StartsWith("**", StringComparison.Ordinal))
            {
                target.Inlines.Add(new Run(value.Substring(2, value.Length - 4)) { FontWeight = FontWeights.SemiBold });
            }
            else
            {
                var labelEnd = value.IndexOf("](", StringComparison.Ordinal);
                target.Inlines.Add(new Run(value.Substring(1, labelEnd - 1))
                {
                    TextDecorations = TextDecorations.Underline,
                });
            }
            cursor = match.Index + match.Length;
        }
        if (cursor < text.Length) target.Inlines.Add(new Run(text.Substring(cursor)));
    }
}

internal static class PacmonVisuals
{
    public static void ApplyToolWindowTheme(FrameworkElement element)
    {
        element.SetResourceReference(Panel.BackgroundProperty, VsBrushes.ToolWindowBackgroundKey);
        element.SetResourceReference(TextElement.ForegroundProperty, VsBrushes.ToolWindowTextKey);
    }

    public static void ApplyBorderTheme(Border border) =>
        border.SetResourceReference(Border.BorderBrushProperty, VsBrushes.CommandBarBorderKey);

    public static void ApplyNativeControlStyles(DependencyObject root)
    {
        if (root is Expander expander)
            expander.SetResourceReference(Control.ForegroundProperty, VsBrushes.ToolWindowTextKey);

        switch (root)
        {
            case Button button:
                button.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.ButtonStyleKey);
                break;
            case CheckBox checkBox:
                checkBox.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.CheckBoxStyleKey);
                break;
            case ComboBox comboBox:
                comboBox.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.ComboBoxStyleKey);
                break;
            case TextBox textBox:
                textBox.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.TextBoxStyleKey);
                break;
            case ProgressBar progressBar:
                progressBar.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.ProgressBarStyleKey);
                break;
            case ListView listView:
                listView.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.ThemedDialogListViewStyleKey);
                break;
            case ScrollViewer scrollViewer:
                scrollViewer.SetResourceReference(FrameworkElement.StyleProperty, VsResourceKeys.ScrollViewerStyleKey);
                break;
        }

        foreach (var child in LogicalTreeHelper.GetChildren(root).OfType<DependencyObject>())
            ApplyNativeControlStyles(child);
    }
}
