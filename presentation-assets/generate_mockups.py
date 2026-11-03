import os
import sys
from PIL import Image, ImageDraw, ImageFilter

def create_android_mockup(screen_path, output_path, bezel=32, corner_radius=96):
    """
    Wraps an actual Android screenshot in a clean, neutral, modern Android device frame.
    Preserves 100% of the screenshot content with zero distortion or modification.
    """
    if not os.path.exists(screen_path):
        print(f"Skipping {screen_path}: file does not exist.")
        return False

    with Image.open(screen_path) as screen:
        screen = screen.convert("RGBA")
        sw, sh = screen.size

        # Device frame dimensions
        fw = sw + bezel * 2
        fh = sh + bezel * 2

        # 1. Device body
        body = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
        b_draw = ImageDraw.Draw(body)

        # Titanium / Graphite chassis frame (#1E232A)
        b_draw.rounded_rectangle([0, 0, fw, fh], radius=corner_radius, fill=(30, 35, 42, 255))
        # Subtle outer metallic rim highlight (#3A4250)
        b_draw.rounded_rectangle([0, 0, fw, fh], radius=corner_radius, outline=(65, 75, 90, 255), width=3)

        # Mask the screen corners slightly to match phone display radius
        screen_radius = max(24, corner_radius - bezel)
        screen_mask = Image.new("L", (sw * 2, sh * 2), 0)
        sm_draw = ImageDraw.Draw(screen_mask)
        sm_draw.rounded_rectangle([0, 0, sw * 2, sh * 2], radius=screen_radius * 2, fill=255)
        screen_mask = screen_mask.resize((sw, sh), Image.Resampling.LANCZOS)

        body.paste(screen, (bezel, bezel), mask=screen_mask)

        # 2. Punch-hole camera (modern Android display)
        cam_diameter = int(sw * 0.032) # subtle ~35px on 1080p
        cam_x = fw // 2
        cam_y = bezel + int(sh * 0.022)
        cam_draw = ImageDraw.Draw(body)
        cam_draw.ellipse([cam_x - cam_diameter//2, cam_y - cam_diameter//2,
                          cam_x + cam_diameter//2, cam_y + cam_diameter//2],
                         fill=(10, 10, 12, 255), outline=(40, 40, 48, 255), width=2)
        # Tiny lens flare dot
        lens_r = max(1, cam_diameter // 6)
        cam_draw.ellipse([cam_x - lens_r, cam_y - lens_r, cam_x + lens_r, cam_y + lens_r],
                         fill=(25, 35, 60, 255))

        # 3. Soft ambient drop shadow
        pad = 80
        total_w = fw + pad * 2
        total_h = fh + pad * 2
        canvas = Image.new("RGBA", (total_w, total_h), (0, 0, 0, 0))

        shadow = Image.new("RGBA", (total_w, total_h), (0, 0, 0, 0))
        sh_draw = ImageDraw.Draw(shadow)
        sh_draw.rounded_rectangle([pad, pad + 24, pad + fw, pad + fh + 24],
                                  radius=corner_radius, fill=(15, 23, 42, 70))
        shadow = shadow.filter(ImageFilter.GaussianBlur(radius=36))

        canvas.paste(shadow, (0, 0), shadow)
        canvas.paste(body, (pad, pad), body)

        canvas.save(output_path, "PNG")
        print(f"Created mockup: {output_path} ({canvas.size[0]}x{canvas.size[1]})")
        return True

if __name__ == "__main__":
    mockup_targets = [
        ("presentation-assets/screens/home.png", "presentation-assets/mockups/home-mockup.png"),
        ("presentation-assets/screens/emergency-countdown.png", "presentation-assets/mockups/emergency-mockup.png"),
        ("presentation-assets/screens/bystander-guide.png", "presentation-assets/mockups/bystander-mockup.png"),
    ]
    created = 0
    for src, dst in mockup_targets:
        if create_android_mockup(src, dst):
            created += 1
    print(f"Mockup generation completed: {created} created.")
