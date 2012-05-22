#!/usr/bin/perl

# Quick insertion of templates from markdown.

my $title = shift;
my $file = shift;
my $out = shift;

open (OUT, ">$out") || die "Cannot open $out : $!\n";

print OUT <<EOF;
<!doctype html>
<html lang="en">
<head>
<title>$title</title>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1.0"/>
<link rel="stylesheet" href="css/style.css"/>
</head>
<body>
EOF

open (MARK, "/usr/local/bin/markdown $file|")
    || die "Cannot run markdown : $!\n";
while (<MARK>) {
    print OUT;
}
close(MARK);

print OUT <<EOF;
</body>
</html>
EOF

