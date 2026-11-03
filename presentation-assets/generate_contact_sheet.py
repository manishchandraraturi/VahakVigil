import os
import glob
from PIL import Image, ImageDraw, ImageFont

def generate_contact_sheet():
    screens_dir = "presentation-assets/screens"
    output_path = os.path.join(screens_dir, "overview-contact-sheet.png")

    ordered_screens = [
        ("home.png", "Home Screen"),
        ("live-ride.png", "Live Ride & Map"),
        ("emergency-countdown.png", "Emergency Countdown (75s)"),
        ("bystander-guide.png", "Bystander First-Aid Guide"),
        ("medical-profile.png", "Medical Profile"),
        ("emergency-contacts.png", "Emergency Contacts"),
        ("profile.png", "Rider Profile"),
        ("ride-history.png", "Ride History"),
        ("settings.png", "Settings & Calibration"),
        ("sensor-dashboard.png", "Sensor Dashboard"),
    ]

    available = []
    for filename, label in ordered_screens:
        p = os.path.join(screens_dir, filename)
        if os.path.exists(p):
            available.append((p, label))

    # Also grab any other pngs in screens_dir except overview-contact-sheet.png
    existing_paths = {p for p, _ in available}
    for p in glob.glob(os.path.join(screens_dir, "*.png")):
        if "overview-contact-sheet" not in p and p not in existing_paths:
            base = os.path.basename(p).replace(".png", "").replace("-", " ").title()
            available.append((p, base))

    if not available:
        print("No screenshots available to generate contact sheet.")
        return False

    n = len(available)
    # Determine grid: 1-4: 1 row or 2 cols, 5-8: 2 rows x 4 cols, 9-10: 2 rows x 5 cols
    if n <= 3:
        cols, rows = n, 1
    elif n <= 4:
        cols, rows = 2, 2
    elif n <= 8:
        cols, rows = 4, 2
    else:
        cols, rows = 5, 2

    thumb_w = 400
    # Assume 9:20 or 9:19.5 aspect ratio (~880px height)
    sample_im = Image.open(available[0][0])
    orig_w, orig_h = sample_im.size
    aspect = orig_h / orig_w
    thumb_h = int(thumb_w * aspect)

    gap_x = 48
    gap_y = 64
    margin_x = 60
    margin_y = 80
    header_h = 100
    label_h = 44

    total_w = margin_x * 2 + cols * thumb_w + (cols - 1) * gap_x
    total_h = margin_y * 2 + header_h + rows * (thumb_h + label_h) + (rows - 1) * gap_y

    canvas = Image.new("RGBA", (total_w, total_h), (18, 9, 15, 255)) # VahakVigil Dark background
    draw = ImageDraw.Draw(canvas)

    # Header
    draw.text((margin_x, margin_y), "VAHAKVIGIL — SYSTEM UI OVERVIEW", fill=(255, 251, 255, 255))
    draw.text((margin_x, margin_y + 36), "Native Android Motorcycle Crash Detection & Emergency Response UI Architecture", fill=(216, 200, 209, 255))

    for idx, (path, label) in enumerate(available):
        r = idx // cols
        c = idx % cols
        x = margin_x + c * (thumb_w + gap_x)
        y = margin_y + header_h + r * (thumb_h + label_h + gap_y)

        with Image.open(path) as im:
            im = im.convert("RGBA")
            thumb = im.resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
            
            # Subtle card border around screenshot
            card_draw = ImageDraw.Draw(thumb)
            card_draw.rectangle([0, 0, thumb_w - 1, thumb_h - 1], outline=(78, 49, 68, 255), width=2)
            
            canvas.paste(thumb, (x, y))

        # Label below screenshot
        label_y = y + thumb_h + 12
        draw.text((x + 4, label_y), f"{idx + 1}. {label}", fill=(248, 248, 248, 255))

    canvas.save(output_path, "PNG")
    print(f"Generated contact sheet: {output_path} ({total_w}x{total_h}) with {n} screens.")
    return True

if __name__ == "__main__":
    generate_contact_sheet()
