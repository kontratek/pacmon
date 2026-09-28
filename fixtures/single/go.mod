module example.com/pacmon-single

go 1.24

require github.com/spf13/cobra v1.8.1

require (
	github.com/jackc/pgx/v5 v5.7.1
	golang.org/x/tools v0.28.0 // indirect
)

tool golang.org/x/tools/cmd/stringer

replace github.com/jackc/pgx/v5 => ../pgx
