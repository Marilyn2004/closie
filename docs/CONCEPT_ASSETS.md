# Original concept asset provenance

Created 2026-10-27 with the built-in imagegen tool for this project. These are original pre-generated concepts, not downloaded retailer photographs, not inventory, and not proof of the application's Gemini API working. The tool selected its internal model; an API model identifier was not returned. Assets live in `src/main/resources/static/concepts/`.

Each asset used this common prompt:

> Create one original clothing CONCEPT asset for Closie, California Campus / Playful Feminine Casual. Professional realistic product photography, single garment or single pair of shoes only, entire item centered and fully visible with generous white margins, clean pure white background, soft studio light, realistic construction stitching and fabric, no person, no mannequin, no visible body parts, no hangers, no props, no brand logos, no text or labels. Square composition. Attractive wearable proportions.

Individual subjects:

- `blue-hoodie.png`: baby blue cropped zip-up hoodie, softly boxy wearable cotton fleece, ribbed cuffs and waistband, silver zipper, small tonal embroidered star on sleeve, no lettering.
- `pink-cardigan.png`: blush pink fitted ribbed-knit cardigan, small pearl-like buttons, scoop neck, long sleeves, slightly cropped.
- `denim-jeans.png`: light-wash mid-rise relaxed straight-leg full-length jeans, realistic denim, copper hardware, clean hems, no distressing.
- `pink-skirt.png`: powder pink pleated opaque cotton mini skirt, believable waistband, girly preppy, no belt.
- `white-sneakers.png`: matched cream/white low-top sneakers, unbranded canvas and suede, pale blue accent, rubber soles and laces.
- `cream-bag.png`: butter-cream shoulder bag, short curved strap, silver hardware, softly structured leather, campus proportions.

Agent visual inspection: all six files display a complete item/pair, plain background, no visible person or logo, coherent pastel palette. This is qualitative inspection, not a numerical quality benchmark or user aesthetic approval. All six still require the user's aesthetic approval.

Application-generated images use separate private asset storage, with actual provider/model metadata attached to the corresponding revision/job. No generated concept receives invented commerce facts.
