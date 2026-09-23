defmodule Umbrella.Accounts.MixProject do
  use Mix.Project

  def project do
    [app: :accounts, deps: deps()]
  end

  defp deps do
    [
      {:ecto_sql, "~> 3.13"}
    ]
  end
end
