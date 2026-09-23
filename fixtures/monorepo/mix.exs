defmodule Umbrella.MixProject do
  use Mix.Project

  def project do
    [apps_path: "apps", deps: deps()]
  end

  defp deps do
    [
      {:jason, "~> 1.4"}
    ]
  end
end
