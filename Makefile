spec.html: md/spec.md scripts/gen.pl
	perl -w scripts/gen.pl "Thormor Vault Specification" md/spec.md spec.html

clean:
	rm -f spec.html
