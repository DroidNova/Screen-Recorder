# Milestone 3: design system and adaptive navigation

## Structure and design goals

The single-activity app remains rooted at `MainActivity`, while `ui/ScreenRecorderApp.kt`
owns its scaffold and navigation host. The `ui/theme` and `ui/navigation` packages own
small reusable foundations, and the stateless Home, Recordings, and Settings screens
live in corresponding `feature` packages. There are no ViewModels, repositories, or
artificial dependency-injection bindings because this milestone has no runtime state.

The visual direction is calm, readable, and restrained. Material semantic roles are
used throughout rather than screen-specific colors. Page, section, card, component,
and small spacing values form a deliberately small token set, alongside 8, 16, and
28 dp corner shapes.

## Brand, color, and typography

The verified brand green is `#388E3C`, taken from the audited legacy
`md_theme_light_primary` resource documented by the Milestone 1 asset inventory and
confirmed in the preserved `ff1dc90` source. The light palette pairs it with neutral
green-tinted surfaces. The intentionally designed dark palette uses a near-black
`#101510` background, lighter green primary, and separate content/outline roles; it is
not an inversion and does not use pure black. Dynamic color is intentionally disabled
so the DroidNova identity remains recognizable. Matching platform launch backgrounds
avoid a bright flash before Compose draws.

The audit found Lato files but no in-repository license. They remain excluded. The app
uses the system sans-serif family with Material 3 scalable `sp` typography and a clear
headline, title, body, and label hierarchy.

## Navigation and adaptive behavior

`TopLevelDestination` is the immutable source of truth for route, localized label,
selected icon, and unselected icon. A remembered `NavHostController` starts at Home;
selection comes directly from its current back stack. Top-level navigation uses
`launchSingleTop`, start-destination `popUpTo`, state save, and state restore. A tap on
the already selected item does not navigate again, while system Back continues to use
Navigation Compose behavior.

Material 3 Adaptive `NavigationSuiteScaffold` chooses the appropriate navigation
component from current window adaptive information: compact windows receive bottom
navigation and wider windows receive the library's rail/navigation treatment. No
device or orientation checks are duplicated. The controller survives layout changes,
safe drawing insets are respected, and content is centered with an 840 dp maximum
width for comfortable expanded-window reading.

## Accessibility

Standard Material navigation and button components provide focus behavior and minimum
touch targets. Navigation exposes selected semantics and localized labels. The
recording action is truly disabled rather than inert. Meaningful empty-state artwork
has a localized description; icons already announced by adjacent labels are marked
decorative to avoid duplicate speech. Headings identify important sections. Scrollable
screen content, scalable text, start-relative padding, non-color status copy, and
flexible layouts support large fonts, TalkBack, and RTL.

## Dependencies

- `androidx.navigation:navigation-compose:2.9.8` supplies the authoritative Compose
  navigation host and controller.
- `androidx.compose.material3:material3-adaptive-navigation-suite`, versioned by the
  existing Compose BOM, supplies adaptive bottom-bar/rail navigation.
- Compose UI test, AndroidX test runner/JUnit integration, Espresso, and JUnit 4 are
  test-scoped dependencies used for semantics behavior and route uniqueness tests.

No production dependency outside the milestone's two allowed navigation additions was
introduced, and no toolchain or SDK version changed.

## Intentionally non-functional

The Home values are display-only proposed defaults. Start recording is disabled and
has no callback into permissions, MediaProjection, services, files, or settings.
Recordings does not access MediaStore or storage. Settings rows are explicitly read-only
and do not persist anything. Recording, permissions, audio, notifications, playback,
file actions, migration, monetization, analytics, and all later-milestone behavior
remain deferred.

## Automated validation

The unit test checks unique destination routes. Compose instrumentation tests cover the
Home start state and selected item, all three destinations, empty-state icon semantics,
the disabled recording action, returning Home, and repeated destination taps. Build,
unit-test, lint, release-build, and diff-check results are recorded in the milestone
completion report. Instrumentation is run only when an already available target exists.

## Manual device-test checklist

- [ ] Light theme
- [ ] Dark theme
- [ ] Home, Recordings, and Settings navigation
- [ ] System Back navigation
- [ ] Repeated selected-tab taps
- [ ] Portrait layout
- [ ] Landscape layout
- [ ] Split screen, if available
- [ ] Tablet/expanded width
- [ ] 100%, 150%, and 200% font scale
- [ ] TalkBack labels and traversal order
- [ ] RTL layout
- [ ] Keyboard/D-pad focus, where available
- [ ] No bright dark-mode launch flash
- [ ] Disabled recording button produces no recording side effects
