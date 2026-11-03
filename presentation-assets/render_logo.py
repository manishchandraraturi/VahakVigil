import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

def cubic_bezier(p0, p1, p2, p3, num_points=50):
    t = np.linspace(0, 1, num_points)
    x = (1-t)**3 * p0[0] + 3*(1-t)**2 * t * p1[0] + 3*(1-t) * t**2 * p2[0] + t**3 * p3[0]
    y = (1-t)**3 * p0[1] + 3*(1-t)**2 * t * p1[1] + 3*(1-t) * t**2 * p2[1] + t**3 * p3[1]
    return list(zip(x, y))

def render_logo(size=1024, scale_factor=4, transparent_bg=True, full_adaptive=False):
    canvas_size = size * scale_factor
    im = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)

    if full_adaptive:
        # Dual-tone background
        # 108dp viewport. y=40dp is boundary
        y_split = canvas_size * (40.0 / 108.0)
        draw.rectangle([0, 0, canvas_size, y_split], fill=(0x12, 0x09, 0x0F, 255))
        draw.rectangle([0, y_split, canvas_size, canvas_size], fill=(0x24, 0x13, 0x1F, 255))
    elif not transparent_bg:
        draw.rectangle([0, 0, canvas_size, canvas_size], fill=(0x12, 0x09, 0x0F, 255))

    # The original viewport is 108x108.
    # The motorcycle bounds in 108x108:
    # x: 24 to 84 (width 60, center 54)
    # y: 43 to 79 (height 36, center 61)
    
    # In adaptive icon, viewport is 108x108, center is (54, 54).
    # If rendering just the emblem centered:
    if transparent_bg:
        # Center the emblem itself:
        # Bounding box width = 60, height = 36.
        # Let's scale it so it fills ~72% of the canvas width
        target_w = canvas_size * 0.72
        scale = target_w / 60.0
        # motorcycle center in original coords: x=54, y=60.5
        cx_orig, cy_orig = 54.0, 60.5
        cx_target, cy_target = canvas_size / 2.0, canvas_size / 2.0
    else:
        # Standard adaptive icon layout (108x108)
        scale = canvas_size / 108.0
        cx_orig, cy_orig = 0.0, 0.0
        cx_target, cy_target = 0.0, 0.0

    def tx(x, y):
        if transparent_bg:
            return (cx_target + (x - cx_orig) * scale, cy_target + (y - cy_orig) * scale)
        else:
            return (x * scale, y * scale)

    # Line widths
    w_ground = max(1.0, 2.0 * scale)
    w_chassis = max(1.0, 2.5 * scale)
    r_wheel = 9.0 * scale

    # 1. Ground line: M24,78 L84,78
    p1 = tx(24, 78)
    p2 = tx(84, 78)
    draw.line([p1, p2], fill=(0xF8, 0xF8, 0xF8, int(255 * 0.40)), width=int(round(w_ground)))

    # 2. Rear wheel: center (37, 68), radius 9
    c_rear = tx(37, 68)
    draw.ellipse([c_rear[0] - r_wheel, c_rear[1] - r_wheel, c_rear[0] + r_wheel, c_rear[1] + r_wheel],
                 outline=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)))

    # 3. Front wheel: center (71, 68), radius 9
    c_front = tx(71, 68)
    draw.ellipse([c_front[0] - r_wheel, c_front[1] - r_wheel, c_front[0] + r_wheel, c_front[1] + r_wheel],
                 outline=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)))

    # 4. Chassis lines & curves
    # Segment A: (71,68) -> (64,48) -> (59,45)
    pts_a = [tx(71, 68), tx(64, 48), tx(59, 45)]
    draw.line(pts_a, fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")

    # Segment B: (64,48) C(60,44 54,43 50,47)
    curve_b = cubic_bezier(tx(64, 48), tx(60, 44), tx(54, 43), tx(50, 47), num_points=60)
    # Segment C: C(46,50 43,53 43,53)
    curve_c = cubic_bezier(tx(50, 47), tx(46, 50), tx(43, 53), tx(43, 53), num_points=30)
    # Segment D: C(39,52 35,49 32,46)
    curve_d = cubic_bezier(tx(43, 53), tx(39, 52), tx(35, 49), tx(32, 46), num_points=30)
    
    top_spine = curve_b + curve_c[1:] + curve_d[1:] + [tx(30, 48)]
    draw.line(top_spine, fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")

    # Segment E: (30,48) C(34,53 38,58 45,58)
    curve_e = cubic_bezier(tx(30, 48), tx(34, 53), tx(38, 58), tx(45, 58), num_points=40)
    draw.line(curve_e, fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")

    # Segment F: (45,58) -> (71,68)
    draw.line([tx(45, 58), tx(71, 68)], fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")

    # Segment G: (45,58) -> (37,68)
    draw.line([tx(45, 58), tx(37, 68)], fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")

    # High quality downsampling
    im_resized = im.resize((size, size), Image.Resampling.LANCZOS)
    return im_resized

def render_icon(size=512, corner_radius=115):
    # Renders the full adaptive icon clipped to a rounded squircle
    scale = 4
    canvas_size = size * scale
    im = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)

    # 1. Dual-tone background
    y_split = canvas_size * (40.0 / 108.0)
    draw.rectangle([0, 0, canvas_size, y_split], fill=(0x12, 0x09, 0x0F, 255))
    draw.rectangle([0, y_split, canvas_size, canvas_size], fill=(0x24, 0x13, 0x1F, 255))

    # 2. Foreground emblem in 108x108 adaptive coordinates
    # Center of viewport is 54, 54
    scale_factor = canvas_size / 108.0
    def tx(x, y):
        return (x * scale_factor, y * scale_factor)

    w_ground = max(1.0, 2.0 * scale_factor)
    w_chassis = max(1.0, 2.5 * scale_factor)
    r_wheel = 9.0 * scale_factor

    # Ground line
    draw.line([tx(24, 78), tx(84, 78)], fill=(0xF8, 0xF8, 0xF8, int(255 * 0.40)), width=int(round(w_ground)))

    # Wheels
    c_rear = tx(37, 68)
    draw.ellipse([c_rear[0] - r_wheel, c_rear[1] - r_wheel, c_rear[0] + r_wheel, c_rear[1] + r_wheel],
                 outline=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)))
    c_front = tx(71, 68)
    draw.ellipse([c_front[0] - r_wheel, c_front[1] - r_wheel, c_front[0] + r_wheel, c_front[1] + r_wheel],
                 outline=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)))

    # Chassis
    draw.line([tx(71, 68), tx(64, 48), tx(59, 45)], fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")
    curve_b = cubic_bezier(tx(64, 48), tx(60, 44), tx(54, 43), tx(50, 47), num_points=60)
    curve_c = cubic_bezier(tx(50, 47), tx(46, 50), tx(43, 53), tx(43, 53), num_points=30)
    curve_d = cubic_bezier(tx(43, 53), tx(39, 52), tx(35, 49), tx(32, 46), num_points=30)
    draw.line(curve_b + curve_c[1:] + curve_d[1:] + [tx(30, 48)], fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")
    curve_e = cubic_bezier(tx(30, 48), tx(34, 53), tx(38, 58), tx(45, 58), num_points=40)
    draw.line(curve_e, fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")
    draw.line([tx(45, 58), tx(71, 68)], fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")
    draw.line([tx(45, 58), tx(37, 68)], fill=(0xF8, 0xF8, 0xF8, 255), width=int(round(w_chassis)), joint="round")

    # Downsample
    im_icon = im.resize((size, size), Image.Resampling.LANCZOS)

    # Apply squircle mask
    mask = Image.new("L", (size * 2, size * 2), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, size * 2, size * 2], radius=corner_radius * 2, fill=255)
    mask = mask.resize((size, size), Image.Resampling.LANCZOS)

    output = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    output.paste(im_icon, (0, 0), mask=mask)
    return output

def render_icon_preview(canvas_size=1024, icon_size=512):
    # Professional preview on neutral background
    bg = Image.new("RGBA", (canvas_size, canvas_size), (0xF8, 0xFA, 0xFC, 255))
    
    # Soft drop shadow
    shadow_pad = 60
    shadow_size = icon_size + shadow_pad * 2
    shadow = Image.new("RGBA", (shadow_size, shadow_size), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    cr = int(icon_size * (115.0 / 512.0))
    s_draw.rounded_rectangle([shadow_pad, shadow_pad + 16, shadow_pad + icon_size, shadow_pad + icon_size + 16],
                             radius=cr, fill=(15, 23, 42, 45))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=28))

    icon = render_icon(size=icon_size, corner_radius=cr)
    
    # Position centered
    pos_x = (canvas_size - icon_size) // 2
    pos_y = (canvas_size - icon_size) // 2

    bg.paste(shadow, (pos_x - shadow_pad, pos_y - shadow_pad), shadow)
    bg.paste(icon, (pos_x, pos_y), icon)
    return bg

if __name__ == "__main__":
    # 1. app-logo.png (1024x1024 transparent background with centered motorcycle emblem)
    logo_trans = render_logo(size=1024, transparent_bg=True)
    logo_trans.save("presentation-assets/logo/app-logo.png", "PNG")
    print("Saved presentation-assets/logo/app-logo.png (1024x1024)")

    # 1b. app-logo-adaptive.png (1024x1024 full background)
    logo_adapt = render_logo(size=1024, transparent_bg=False, full_adaptive=True)
    logo_adapt.save("presentation-assets/logo/app-logo-adaptive.png", "PNG")
    print("Saved presentation-assets/logo/app-logo-adaptive.png (1024x1024)")

    # 2. app-icon-preview.png (1024x1024 on neutral presentation background)
    icon_prev = render_icon_preview(canvas_size=1024, icon_size=520)
    icon_prev.save("presentation-assets/logo/app-icon-preview.png", "PNG")
    print("Saved presentation-assets/logo/app-icon-preview.png (1024x1024)")
