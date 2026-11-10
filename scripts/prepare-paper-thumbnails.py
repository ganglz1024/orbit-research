"""Generate local first-page previews from the six linked arXiv v1 PDFs.

Run manually with Python + pypdfium2 + Pillow; never runs in the browser/build.
The supplied editorial illustrations are visual references, not PDF previews.
"""
import json
import ssl
from pathlib import Path
from urllib.request import Request, urlopen

import pypdfium2 as pdfium

try:
    import certifi
    TLS_CONTEXT = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    TLS_CONTEXT = ssl.create_default_context()

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ("survey", "2312.10997", "01_rag_survey"),
    ("rag", "2005.11401", "02_retrieval_augmented_generation"),
    ("self-rag", "2310.11511", "03_self_rag"),
    ("crag", "2401.15884", "04_corrective_rag"),
    ("lost-middle", "2307.03172", "05_lost_in_the_middle"),
    ("graph-rag", "2404.16130", "06_graph_rag"),
]


def main():
    cache = ROOT / "tmp" / "pdfs"
    output = ROOT / "public" / "images" / "papers"
    cache.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    manifest = []
    for paper_id, arxiv, filename in SOURCES:
        url = f"https://arxiv.org/pdf/{arxiv}v1"
        pdf_path = cache / f"{arxiv}v1.pdf"
        if not pdf_path.exists():
            with urlopen(Request(url, headers={"User-Agent": "OrbitThumbnailPreparation/1.0"}), timeout=40, context=TLS_CONTEXT) as response:
                data = response.read()
            if not data.startswith(b"%PDF-"):
                raise ValueError(f"Not a PDF: {url}")
            pdf_path.write_bytes(data)
        with pdfium.PdfDocument(pdf_path) as pdf:
            page = pdf[0]
            image = page.render(scale=640 / page.get_width()).to_pil().convert("RGB")
            target = output / f"{filename}.webp"
            image.save(target, "WEBP", quality=85, method=6)
            manifest.append({"id": paper_id, "source": url, "page": 1,
                             "image": f"/images/papers/{filename}.webp",
                             "width": image.width, "height": image.height})
            print(f"{paper_id}: {image.width}x{image.height}, {target.stat().st_size} bytes", flush=True)
            page.close()
    (output / "sources.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
