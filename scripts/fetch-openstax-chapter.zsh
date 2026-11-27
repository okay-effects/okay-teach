#!/bin/zsh
# AN OPENSTAX CHAPTER AS A BOOK OF THE BOOK LOOP (specs/book-loop.md): each section's page → `# <id> <title>` and its
# text; the chapter's "Conceptual Questions" and "Problems & Exercises" pages → each question as a `?? ` line under the
# section it is printed under. Fetched into books/ (OpenStax College Physics 2e is CC BY-NC-SA 4.0: never committed).
# macOS's textutil turns a page into text.
#   scripts/fetch-openstax-chapter.zsh <chapter number> <section slug>…    (default: chapter 2, kinematics)
#   Paul Peter Urone, Roger Hinrichs, College Physics 2e, OpenStax, 2022 — https://openstax.org/books/college-physics-2e
set -e
base=https://openstax.org/books/college-physics-2e/pages
ch=${1:-2}; shift 2>/dev/null || true
slugs=($@)
if [ ${#slugs} -eq 0 ]; then
  slugs=(2-1-displacement 2-2-vectors-scalars-and-coordinate-systems 2-3-time-velocity-and-speed 2-4-acceleration
    2-5-motion-equations-for-constant-acceleration-in-one-dimension
    2-6-problem-solving-basics-for-one-dimensional-kinematics 2-7-falling-objects
    2-8-graphical-analysis-of-one-dimensional-motion)
fi
mkdir -p books
tmp=$(mktemp -d)
out=books/openstax-cp2e-ch$ch.txt
page() { curl -sL -m 60 -o $tmp/p.html $base/$1; textutil -convert txt -output $tmp/$2 $tmp/p.html; }
# the questions of a chapter page, as "<section id><TAB><question>" lines: a section heading "2.3 Title", then
# numbered questions ("12", ".", the text up to the next number or heading). A number starts a question only when it
# is the next expected one: a formula's "1", ".", "80 m/s" lines (its MathML) are the question's text, glued back
questions() {
  awk 'BEGIN { sec = ""; q = ""; want = 0; expect = 1; glue = 0 }
    function flush() { if (sec != "" && q != "") { gsub(/[ \t]+/, " ", q); print sec "\t" q }; q = "" }
    /^[0-9]+\.[0-9]+ [A-Z]/ { flush(); split($0, a, " "); sec = a[1]; want = 0; next }
    /^[0-9]+$/ && $0 == expect { flush(); want = 1; expect++; next }
    /^\.$/ && want == 1 { want = 2; next }
    /^(PreviousNext|Order a print copy|Citation\/Attribution)/ { flush(); sec = ""; next }
    want == 2 && /^[0-9]+$/ { q = q " " $0; glue = 1; next }
    want == 2 && /^\.$/ { q = q "."; glue = 1; next }
    want == 2 && glue { q = q $0; glue = 0; next }
    want == 2 { q = q " " $0 }
    END { flush() }' $tmp/$1
}
page $ch-conceptual-questions cq.txt; questions cq.txt > $tmp/q.tsv
page $ch-problems-exercises pe.txt; questions pe.txt >> $tmp/q.tsv
{ echo "#! OpenStax College Physics 2e, chapter $ch (Urone, Hinrichs, 2022) — CC BY-NC-SA 4.0 — $base"
  for s in $slugs; do
    page $s s.txt
    title=$(grep -m1 -E "^$ch\.[0-9]+ " $tmp/s.txt)
    id=${title%% *}
    from=$(grep -n -m2 -E "^$ch\.[0-9]+ " $tmp/s.txt | tail -1 | cut -d: -f1)
    to=$(grep -n -m1 "^PreviousNext" $tmp/s.txt | cut -d: -f1)
    echo "# $title"
    sed -n "$((from + 1)),$((to - 1))p" $tmp/s.txt | grep -v "^??"
    awk -F'\t' -v id="$id" '$1 == id { print "?? " $2 }' $tmp/q.tsv
  done; } > $out
rm -rf $tmp
echo "$out: $(grep -c '^# ' $out) sections, $(grep -c '^?? ' $out) questions, $(wc -l < $out) lines"
