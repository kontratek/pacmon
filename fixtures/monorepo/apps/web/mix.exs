defmodule Umbrella.Web.MixProject do
  use Mix.Project

  def project do
    [app: :web, deps: deps()]
  end

  defp deps do
    [
      {:phoenix, "~> 1.8"}
    ]
  end
end
