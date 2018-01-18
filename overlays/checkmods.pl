#! /usr/bin/perl

my $src = "/usr/local/src/vula_src/branches/vula-12.x";

while (<>) {

 my $file = $_;
 chomp($file);
 # print "checking $src/$file\n";

 if ($file eq ".project" ) { next; }

 if ($file =~ /_en.properties$/) {
	#print "properties override $file\n";
	$file =~ s/_en//;
 	unless (-e "$src/$file") {
 		print "$src/$file does not exist\n";
	 }
 } else {

 	unless (-e "$src/$file") {
 		print "$src/$file does not exist\n";
	 }
 }
}
