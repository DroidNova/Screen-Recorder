# Asset and visual-identity inventory

Inventory was derived without modifying assets. Vector dimensions are viewport/resource-defined; raster dimensions should be preserved from the source metadata in the future asset register. Licensing is not documented in-repo, so all third-party-looking artwork/fonts require verification.

| Paths/group | Type / size | Current purpose, compatibility, duplication/accessibility | Recommendation |
|---|---|---|---|
| `app/src/main/ic_launcher-playstore.png` | PNG, ~160 KiB | Play-store source icon; purpose clear; raster appearance/theme/accessibility device review needed | Preserve; licensing must be verified |
| `mipmap-anydpi-v26/ic_launcher*.xml`; `drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`; `mipmap-{m,h,x,xx,xxx}dpi/ic_launcher*.webp` | Adaptive vectors plus density WebP variants | Launcher/round/foreground. Expected density duplication; light/dark monochrome support absent | Preserve baseline; modernize after visual sign-off |
| `home*.xml`, `recordings*.xml`, `settings*.xml`; `color/bottom_nav_color.xml` | Vectors/state-related icons | Bottom navigation includes generic plus filled/outlined variants, but menu points at generic files; selected-state behavior needs capture | Preserve baseline; consolidate/modernize later |
| `ic_recording*.xml`, `ic_stop.xml`, `ic_pause.xml`, `ic_play.xml` | Vectors | Recording/notification controls. Notification actually uses recording + delete icons rather than semantic action icons | Modernize; preserve screenshots |
| `ic_delete.xml`, `ic_download*.xml`, `ic_rename.xml`, `ic_share.xml`, `ic_more_options.xml`, `ic_enter.xml`, `ic_clear.xml`, `ic_check.xml`, `ic_cross.xml`, `ic_back.xml`, `video.xml` | Vectors | File actions/general UI. Download naming indicates apparent duplication. Many ImageViews lack content descriptions | Preserve baseline; consolidate and add semantics in rebuild |
| `ic_rate_us.xml`, `ic_privacy_policy.xml`, `ic_report_bugs.xml`, `ic_share_us.xml`, `ic_version.xml`, `ic_whatsapp.xml`, `ic_info.xml` | Vectors | Settings/information icons. WhatsApp UI hidden; brand licensing unknown | Licensing must be verified; remove unreachable/unused after preservation decision |
| `ic_live_listen.png`, `ic_force_5g.jpeg` | Raster (file metadata should be captured separately) | No source reference found by code/resource search: apparently unused and names imply third-party/brand imagery | Licensing must be verified; likely remove in rebuild |
| `recordings*.xml` and `video.xml` | Vectors | List placeholder/navigation; semantic overlap/apparent duplication | Preserve baseline; modernize |
| `font/lato_regular.ttf` (~76 KiB), `lato_bold.ttf` (~72 KiB) | TTF | Lato regular is global theme; bold referenced in layouts. License file absent | Preserve appearance; licensing must be verified and bundle license if retained |
| `values/colors.xml`, `values-night/color.xml` | Color resources | Green Material palette, white/light surfaces, near-black/green dark surfaces. Duplicate dark definitions and malformed-looking trailing quote characters in several values need build/lint verification; do not fix here | Preserve values; modernize after screenshot/contrast audit |
| `values/themes.xml`, `values-night/themes.xml` | Material3 DayNight styles | Lato, status/navigation colors, light/dark overrides | Preserve baseline; test contrast/system bars |
| `textview_outline.xml`, `bottom_nav_color.xml` | Drawable/color selector | Field outline and nav tint | Preserve; verify states |

All drawable files are XML except the two rasters named above. All mipmap density files are WebP. Important accessibility concerns: recording/file action ImageViews often have no content descriptions, literal text is not localized, color/shape may be the only selection signal, and 150% font reflow/contrast are untested. Asset provenance/license records are entirely absent; “preserve” means archive as evidence, not automatically reuse in a commercial rebuild.
