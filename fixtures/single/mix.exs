defmodule PacmonFixture.MixProject do
  use Mix.Project

  def project do
    [app: :pacmon_fixture, version: "0.1.0", deps: deps()]
  end

  defp deps do
    [
      {:phoenix, "~> 1.8"},
      {:ecto_sql, "~> 3.13"},
      {:wallaby, "~> 0.30", only: :test}
    ]
  end
end
