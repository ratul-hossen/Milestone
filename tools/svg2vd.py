"""Converts the Figma icon SVGs (downloaded via the Figma MCP) into Android VectorDrawables.

Usage: python tools/svg2vd.py <svg_dir> <app/src/main/res/drawable>
Stroke colours become black so icons can be tinted in Compose (Icon(tint = ...)).
"""
import re, sys, os
src, out = sys.argv[1], sys.argv[2]
mapping = {
 'home':'ic_home','history':'ic_history','book':'ic_book','chart':'ic_chart','plus':'ic_plus',
 'flame':'ic_flame_18','flag':'ic_flag_18','more':'ic_more','arrow_left':'ic_arrow_left',
 'lead_book':'ic_book_field','chevron_down_field':'ic_chevron_down','lead_timer':'ic_timer',
 'lead_calendar':'ic_calendar','lead_notes':'ic_notes','err_alert':'ic_alert','chip_check':'ic_check_18',
 'filter':'ic_filter','calendar_red':'ic_calendar_18','chevron_down':'ic_chevron_down_18',
 'info_calendar':'ic_calendar_22','info_timer':'ic_timer_22','info_target':'ic_target_22',
 'notes':'ic_notes_20','trash_btn':'ic_trash_18','edit_btn':'ic_edit_18','trash_dialog':'ic_trash',
 'summary_target':'ic_target_20','close':'ic_close','check_white':'ic_check_20','sheet_target':'ic_target',
 'stat_flame':'ic_flame_stat','stat_chart':'ic_chart_18','stat_target':'ic_target_18',
}
for f,name in mapping.items():
    s = open(os.path.join(src,f+'.svg')).read()
    w,h = re.search(r'width="([\d.]+)" height="([\d.]+)"', s).groups()
    vb = re.search(r'viewBox="0 0 ([\d.]+) ([\d.]+)"', s).groups()
    paths=[]
    for m in re.finditer(r'<path ([^>]*)/>', s):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(1)))
        attrs = [f'android:pathData="{a["d"]}"']
        if 'stroke' in a:
            attrs += ['android:strokeColor="#FF000000"', f'android:strokeWidth="{a.get("stroke-width","1")}"',
                      f'android:strokeLineCap="{a.get("stroke-linecap","butt")}"', f'android:strokeLineJoin="{a.get("stroke-linejoin","miter")}"']
        if a.get('fill','none')!='none':
            attrs.append('android:fillColor="#FF000000"')
        paths.append('    <path\n        ' + '\n        '.join(attrs) + ' />')
    xml = (f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           f'    android:width="{w}dp"\n    android:height="{h}dp"\n    android:viewportWidth="{vb[0]}"\n    android:viewportHeight="{vb[1]}">\n'
           + '\n'.join(paths) + '\n</vector>\n')
    open(os.path.join(out,name+'.xml'),'w').write(xml)
print(len(mapping))
