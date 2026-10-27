# Source data

`functions.xml` is MathWorks' MATLAB function index. `update_docs.py` generates the
bundled docs data from it: `resources/docs/function_list` (completion),
`function_descriptions.properties` (docs popup) and `function_url.properties`
(links to the online documentation).

MathWorks moves pages from time to time (e.g. `ref/abs.html` became
`ref/double.abs.html`). To refresh:

    curl -L --compressed -A "Mozilla/5.0" -o data/functions.xml \
        https://www.mathworks.com/help/matlab/referencelist_function_cat.xml
    python3 data/update_docs.py data/functions.xml resources/docs

`functions.xml` isn't loaded at runtime, so it lives here instead of in `resources/`.
Functions without a known page link to the MathWorks documentation search instead.
