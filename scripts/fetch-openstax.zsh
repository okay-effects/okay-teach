#!/bin/zsh
# THE KINEMATICS SECTION OF OPENSTAX COLLEGE PHYSICS 2e (§2.5), fetched into books/ (not in git: the book is
# CC BY-NC-SA 4.0, this repository Apache-2.0 — the text is read here, never redistributed). macOS's textutil turns
# the page into text; the section runs from its title to the page's navigation.
#   Paul Peter Urone, Roger Hinrichs, College Physics 2e, OpenStax, 2022 — https://openstax.org/books/college-physics-2e
set -e
mkdir -p books
url=https://openstax.org/books/college-physics-2e/pages/2-5-motion-equations-for-constant-acceleration-in-one-dimension
tmp=$(mktemp -d)
curl -sL -m 60 -o $tmp/p.html $url
textutil -convert txt -output $tmp/p.txt $tmp/p.html
from=$(grep -n "^2.5 Motion Equations" $tmp/p.txt | tail -1 | cut -d: -f1)
to=$(grep -n "^PreviousNext" $tmp/p.txt | head -1 | cut -d: -f1)
{ echo "# Paul Peter Urone, Roger Hinrichs, College Physics 2e, OpenStax (2022), §2.5 — CC BY-NC-SA 4.0"
  echo "# $url"
  sed -n "${from},$((to - 1))p" $tmp/p.txt; } > books/openstax-cp2e-2.5.txt
rm -rf $tmp
echo "books/openstax-cp2e-2.5.txt: $(wc -l < books/openstax-cp2e-2.5.txt) lines"
