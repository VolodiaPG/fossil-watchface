"""Generate dial background, tick, battery and complication icons into build/files/icons."""
import math, os, subprocess, sys
from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICONS = os.path.join(ROOT, 'build/files/icons')
COMPRESS = os.path.join(os.environ.get('SDK', os.path.join(ROOT, '../sdk')), 'tools/image_compress.py')
S = 4  # supersample, then threshold (display has no alpha blending)


def finish(img, size, name):
    """Box-downsample (no half-pixel shift), hard alpha threshold, write RLE icon."""
    img = img.resize((size, size), Image.BOX)
    img.putalpha(img.getchannel('A').point(lambda a: 255 if a >= 128 else 0))
    png = '/tmp/%s.png' % name
    img.save(png)
    subprocess.check_call([sys.executable, COMPRESS, '-i', png, '-o', os.path.join(ICONS, name), '-w', str(size), '-h', str(size), '-f', 'rle'])


BAT_W, BAT_H = 36, 14   # battery tab: half the date box's height
BAT_R = 8               # same radius as the date box: roundovers where the tab meets it, and the tab's bottom corners


def battery_indicator(name, level):
    """Tab merged into the bottom of the date box (concave roundovers at the junction), holding a small black battery.

    The union of date box and tab is lit as one shape, then only the part below the date box is kept:
    it lines up with the date box drawn on the background, so the two read as one piece.
    """
    dw, dh = DATE_BOX[2], DATE_BOX[3]
    x0 = (dw - BAT_W) // 2  # tab left edge inside the date box's width
    r = BAT_R

    def union(d, f):
        d.rounded_rectangle([0, 0, dw * S, dh * S], radius=8 * S, fill=f)
        for left in (True, False):  # fillets: square next to the tab, minus a disc = concave roundover
            sx = (x0 - r) if left else (x0 + BAT_W)
            cx = sx if left else sx + r
            d.rectangle([sx * S, dh * S, (sx + r) * S - 1, (dh + r) * S - 1], fill=f)
            d.ellipse([(cx - r) * S, (dh + r - r) * S, (cx + r) * S - 1, (dh + r + r) * S - 1], fill=0)
        d.rounded_rectangle([x0 * S, (dh - 2 * r) * S, (x0 + BAT_W) * S, (dh + BAT_H) * S], radius=r * S, fill=f)

    full = shadowed(union, dw, dh + BAT_H)
    box = full.crop((x0 - r, dh, x0 + BAT_W + r, dh + BAT_H))  # BAT_W + 2r wide
    d = ImageDraw.Draw(box)
    black = (0, 0, 0, 255)
    ox = r + 6                                   # glyph is centred on the tab
    d.rectangle([ox, 4, ox + 22, 10], outline=black)  # body 23 x 7
    d.rectangle([ox + 23, 6, ox + 24, 8], fill=black)  # nub
    fill = (level * 19) // 8                     # inner width 19 px
    if fill:
        d.rectangle([ox + 2, 6, ox + 1 + fill, 8], fill=black)
    png = '/tmp/%s.png' % name
    box.save(png)
    subprocess.check_call([sys.executable, COMPRESS, '-i', png, '-o', os.path.join(ICONS, name), '-w', str(box.width), '-h', str(box.height), '-f', 'rle'])


# Icon boxes are centred on the dial at TICK_R and must stay inside the 240 px display (no negative
# layout coordinates): 30 px normally, 50 px between 5 and 7 o'clock where the tick reaches further in.
TICK_R = 105
RIM = 117          # outer end of every tick
BANNER_DEPTH = 20  # px the notification banner covers at the bottom of the dial (measured on a photo)
BANNER_GAP = 6
BASE_IN = {'H': 105, 'M': 93}  # inner end of a normal hour (short) / minute (long) tick


def tick_box(angle):
    return 50 if 150 <= angle <= 210 else 30


def tick_inner(kind, angle):
    r_in = BASE_IN[kind]
    c = math.cos(math.radians(angle))
    if 150 <= angle <= 210 and c < 0:  # between 5 and 7 o'clock the banner hides the rim: reach further in
        r_in = min(r_in, (120 - BANNER_DEPTH - BANNER_GAP) / -c)
    return max(r_in, TICK_R - tick_box(angle) / 2 + 2)


def tick_icon(name, kind, angle):
    box = tick_box(angle)
    big = box * S
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    a = math.radians(angle)
    ux, uy = math.sin(a), -math.cos(a)
    c = big / 2
    r0, r1 = (tick_inner(kind, angle) - TICK_R) * S, (RIM - TICK_R) * S
    d.line([c + ux * r0, c + uy * r0, c + ux * r1, c + uy * r1], fill=(255, 255, 255, 255), width=3 * S)
    if kind == 'H':  # small dot (6 px) capping the inner end of the hour tick
        dx, dy = c + ux * (r0 - 2 * S), c + uy * (r0 - 2 * S)
        d.ellipse([dx - 3 * S, dy - 3 * S, dx + 3 * S, dy + 3 * S], fill=(255, 255, 255, 255))
    finish(img, box, name)


# The 15 minutes before the event are three groups of 5 small ticks, all the same size. The watch mixes up
# its images when a face draws many different ones at once, and drops text when one image is huge, so each group is
# a single small image: 3 groups + the hour tick + the minute tick.
GROUP_BOX = 56      # px, square
GROUP_R = 113       # radius of the circle the group's middle tick sits on
GROUP_TICK = 7      # px tall, ending on the rim


def group_origin(first_step):
    """Top-left of the group's box, clamped to the display. The widget computes the same thing."""
    a = math.radians((first_step - 2) * 6)  # middle tick of the 5
    ox = int(math.floor(120 + GROUP_R * math.sin(a) + 0.5)) - GROUP_BOX // 2
    oy = int(math.floor(120 - GROUP_R * math.cos(a) + 0.5)) - GROUP_BOX // 2
    return min(max(0, ox), 240 - GROUP_BOX), min(max(0, oy), 240 - GROUP_BOX)


def group_icon(name, first_step):
    """5 ticks: first_step, first_step - 1, ... (6 degrees apart), ending on the rim."""
    box = GROUP_BOX * S
    img = Image.new('RGBA', (box, box), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ox, oy = group_origin(first_step)
    for j in range(5):
        a = math.radians((first_step - j) * 6)
        ux, uy = math.sin(a), -math.cos(a)
        pts = [((120 + r * ux - ox) * S, (120 + r * uy - oy) * S) for r in (RIM - GROUP_TICK, RIM)]
        d.line([pts[0], pts[1]], fill=(255, 255, 255, 255), width=2 * S)
    finish(img, GROUP_BOX, name)


GREY = 170  # the single shadow grey (2-bit display: 0 / 85 / 170 / 255)


def shadowed(draw_shape, w, h):
    """White shape with a one-grey inner shadow along its top and top-left edge. Returns an RGBA image of w x h."""
    bw, bh = w * S, h * S
    mask = Image.new('L', (bw, bh), 0)
    draw_shape(ImageDraw.Draw(mask), 255)
    lit = Image.new('L', (bw, bh), 0)
    lit.paste(mask, (int(1.5 * S), 3 * S))  # same shape moved down-right (no wrap-around)
    crescent = ImageChops.subtract(mask, lit)             # remains on the top / top-left rim
    shade = Image.new('L', (bw, bh), 255)
    shade.paste(GREY, mask=crescent)
    img = Image.merge('RGBA', (shade, shade, shade, mask)).resize((w, h), Image.BOX)
    r, g, b, a = img.split()
    q = lambda v: GREY if v < (GREY + 255) // 2 else 255
    return Image.merge('RGBA', (r.point(q), g.point(q), b.point(q), a.point(lambda v: 255 if v >= 128 else 0)))


def widget_bubble(name, size=76):
    img = shadowed(lambda d, f: d.ellipse([S, S, size * S - S, size * S - S], fill=f), size, size)
    png = '/tmp/%s.png' % name
    img.save(png)
    subprocess.check_call([sys.executable, COMPRESS, '-i', png, '-o', os.path.join(ICONS, name), '-w', str(size), '-h', str(size), '-f', 'rle'])


def distance_icon(name, size=24):
    """Side-view sneaker, toe to the right."""
    big = size * S
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = (255, 255, 255, 255)
    cut = (0, 0, 0, 0)
    pts = [(2, 5), (9, 5), (10, 9), (14, 11), (19, 13), (22, 16), (22, 20), (2, 20)]
    d.polygon([(x * S, y * S) for x, y in pts], fill=w)
    d.line([(2 * S, 17 * S), (22 * S, 17 * S)], fill=cut, width=S)      # sole line
    for x in (11, 14, 17):                                              # laces
        d.line([(x * S, (8 + (x - 11) * 0.7) * S), ((x + 2) * S, (11 + (x - 11) * 0.7) * S)], fill=cut, width=S)
    finish(img, size, name)


def printer_icon(name, size=24):
    """3D printer: frame with a nozzle over a part on the bed."""
    big = size * S
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = (255, 255, 255, 255)
    cut = (0, 0, 0, 0)
    d.rounded_rectangle([2 * S, 2 * S, 22 * S, 22 * S], radius=2 * S, fill=w)
    d.rectangle([4 * S, 4 * S, 20 * S, 20 * S], fill=cut)                    # window, leaves a 2 px frame
    d.rectangle([4 * S, 7 * S, 20 * S, 9 * S], fill=w)                       # gantry
    d.polygon([(10 * S, 9 * S), (14 * S, 9 * S), (12 * S, 13 * S)], fill=w)  # nozzle
    d.rectangle([9 * S, 16 * S, 15 * S, 20 * S], fill=w)                     # part on the bed
    finish(img, size, name)


DATE_BOX = (141, 106, 70, 28)  # x, y, w, h of the rounded rectangle behind the date


def alarm_icon(name, size=20):
    """Bell."""
    big = size * S
    img = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = (255, 255, 255, 255)
    d.pieslice([4 * S, 2 * S, 16 * S, 14 * S], 180, 360, fill=w)               # dome
    d.polygon([(4 * S, 8 * S), (16 * S, 8 * S), (18 * S, 15 * S), (2 * S, 15 * S)], fill=w)  # flared body
    d.ellipse([8 * S, 16 * S, 12 * S, 19 * S], fill=w)                         # clapper
    d.rectangle([9 * S, 0, 11 * S, 3 * S], fill=w)                             # top knob
    finish(img, size, name)


def background():
    img = Image.new('RGB', (240, 240), (0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([2, 2, 237, 237], outline=(255, 255, 255), width=2)
    box = shadowed(lambda dd, f: dd.rounded_rectangle([0, 0, DATE_BOX[2] * S, DATE_BOX[3] * S], radius=8 * S, fill=f), DATE_BOX[2], DATE_BOX[3])
    img.paste(box.convert('RGB'), (DATE_BOX[0], DATE_BOX[1]), box.getchannel('A'))
    png = '/tmp/bg.png'
    img.save(png)
    subprocess.check_call([sys.executable, COMPRESS, '-i', png, '-o', os.path.join(ICONS, 'background.raw'), '-w', '240', '-h', '240', '-f', 'raw'])


os.makedirs(ICONS, exist_ok=True)
background()
for i in range(60):
    tick_icon('icTkH%d' % i, 'H', i * 6)  # hour: short
    tick_icon('icTkM%d' % i, 'M', i * 6)  # minute: long
    group_icon('icG%d' % i, i)  # 5 small ticks, the first one at minute i
widget_bubble('bgCircle')
distance_icon('icDist')
printer_icon('icPrint')
alarm_icon('icAlarm')
for i in range(9):
    battery_indicator('icBat%d' % i, i)
