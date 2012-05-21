spec.html: md/index.md md/spec.md scripts/gen.pl
	perl -w scripts/gen.pl "Thormor Vaults" md/index.md index.html
	perl -w scripts/gen.pl "Thormor Vault Specification" md/spec.md spec.html

clean:
	rm -f spec.html index.html
