# Pebble Notes changelog

## 0.21.1 · Balanced mobile corners and fluid block moves

- Mobile library clipping uses a tighter 16-dp inward top curve.
- Drag blocks continuously across multiple neighbours and gaps, with interruptible neighbour movement. Images and tables use the same drag handle.
- Idea blocks display compact body-sized copy on mobile while retaining inline emphasis.
- The floating navigation bar returns with a quick fade and upward reveal.
- Top navigation buttons use a lighter, backdrop-adaptive glass tint.
- Navigation selection has gentle drag resistance and an elastic spring finish.

## 0.21.0 · Smoother Mac scrolling and safer editing

- Mac collection fields, icon choices and helper text remain readable over glass.
- Removing table rows, columns or entire tables safely handles pending cell updates.
- Pressing Enter on an empty toggle child exits the toggle and supports Undo.
- Mac scrolling reuses image previews, isolates scrollbar updates, limits preview text layout and avoids repeated library history reads.
- Native Mac scrolling follows the display refresh rate, including 144 Hz monitors.

## 0.20.0 · Reliable collections and faster mobile notes

- Deleting or renaming collections safely skips permanently erased notes, and older blocked saves recover without restoring erased content.
- Phone tables have compact columns and aligned rows, with row and column menus for insertion and deletion.
- Return on an empty toggle child exits one nesting level; undo restores it.
- The phone library scrolls within rounded top corners below its fixed heading, without header blur.
- Wallpaper thumbnails decode off the UI thread and share a bounded cache; library filtering and card indexing avoid repeated work.
- Block dragging previews nearby movement and saves once on release, while note reveals keep text at its natural proportions.
- The phone About page matches desktop, option dividers are clearer, and the navigation capsule follows touch gestures.
- Appearance includes Standard or up-to-120-Hz refresh rate; rounded library corners have a deeper curve.

## 0.19.0 · Search and a colourful library

- Search joins Home, Tasks and Settings in the phone navigation capsule; compose stays separate.
- Search opens a dedicated page with the keyboard and live note results in your current card or list view.
- A softer home glow lets scrolling cards show through the faded header blur.
- Mac sidebar counts show notes in tabs and nested collections, with an option to hide them in Appearance settings.

## 0.18.0 · Fluid mobile navigation

- Phone notes expand from the tapped card with rounded corners and gently appearing controls; page changes dissolve quickly instead of sliding sideways.
- Scrolling content extends behind the status bar while header controls stay safely inset. Wallpaper and system bar colours transition together.
- A floating glass navigation capsule offers Notes, Tasks and Settings with a spring selection and swipe switching. Search and compose remain separate actions.
- Phone block handles move to the right edge and open options with a tap; body text aligns with the note title.

## 0.17.0 · Steady menus and useful blocks

- Mac context menus use native submenu tracking and no longer crash when the editor copies them; Style closes from every picker.
- Note cards reveal quickly once and stay visible across tabs, folders and scrolling.
- Quotes and Idea blocks preserve editable rich text, undo and sync.
- Phone formatting offers visible highlight colours, a link field and consistent list controls; block options stay beside their text.
- Image backdrops fade away with the editor and system bars when going back. Dark documents receive complementary light paper when choosing a backdrop.
- Phone cards have bounded heights and a Masonry or Grid setting. Scrolling closes swipe actions; settings dividers and reset buttons are consistent.

## 0.16.0 · Backdrops and thoughtful details

- Choose from eight image wallpapers or upload a custom note backdrop; images travel with sync and backups.
- Style selectors have full hit areas, clearer labels, working gradient endpoint controls and a visible reset button.
- Card entrances are more deliberate, and folder labels retain space at the bottom of coloured previews.
- Phone titles wrap inside the document and style shadows have room to fade.
- Menus use neutral icons and subtle contextual highlights; Trash offers a confirmed Empty Trash button.
- Phone status bars take the prominent wallpaper colour or the top gradient colour, with contrasting system icons.

## 0.15.2 · Style and motion, repaired

- Style menus open beneath the paintbrush, remain inside the Mac window, and respond to every colour option.
- Phone navigation uses lighter translucent glass without opaque strips over coloured notes.
- Document colours and the transition between seamless pages and rounded cards animate smoothly.
- Library cards appear with a quick staggered fade when changing folders.
- Sidebar icon surfaces expand fluidly into the active row.

## 0.15.1 · Styles, refined

- Styled note cards now layer the document through the lower edge, leaving the backdrop around the top and sides.
- Backdrop gradients flow from top to bottom and use a softer same-colour fade around fixed writing controls.
- Style menus stay inside the app, open beside the paintbrush, and use clearer sections, dividers and previews.
- Backdrop, document and text colours now have separate palettes matched to their intended role.
- Phone navigation controls use adaptive translucent materials over every note colour.

## 0.15.0 · A colour for every thought

- Notes have independent document, backdrop and text colours, saved and synced with each note.
- A paintbrush opens a temporary Style menu with a live preview, colour palettes and custom colours.
- Choose a solid or gradient backdrop to frame the document as a rounded card on Mac and phone.
- Library previews reflect each note’s colours. Reset style returns to the original appearance.

## 0.14.6 · Phone details, refined

- Task descriptions begin at the top of the writing area.
- Toggle arrows and nested content align with their text.
- Image zoom controls stay fixed above the image.
- Writing and image controls use smooth pill tracks and round slider handles.
- Back gestures return from tasks and library sections to Notes.

## 0.14.5 · Tasks, together

- Task cards open the task editor on Mac and Android, including from tag pages. Descriptions have more room to write.
- Repeat offers daily, weekly, monthly and yearly schedules, even before choosing a date. Completing a repeating task advances its due date.
- Tag pages include matching tasks alongside notes.
- Mac menus show action icons; task menus include Delete. The inner frame follows the outer corner radius.
- Completely blank notes move to Trash when you leave them. Titles, tags, attachments and tables are preserved.
- Android menus use compact icon tiles and subtle dividers, with more filled icons and restrained spring motion.
- About and clipboard suggestions use shorter copy, and About shows the version once.

## 0.14.4 · A calmer phone

- Phone task options use compact status and priority icons, grouped scheduling controls and a concise action toolbar.
- Task details use icon controls that fit narrow screens.
- Notes open as full pages without a raised card edge. Tighter block spacing keeps headings and toggle children together.
- Removed the add-inside-toggle prompt; Return still creates a toggle child.
- Sheet dimming, blur and motion now animate together, with reduced-motion support. Profile settings use shorter labels.
- Empty-state previews use solid light and dark cards on phone too, so the stacked artwork stays clean.

## 0.14.3 · At your fingertips

- Mac keyboard shortcuts include sidebar, search, new notes and navigation. Find the full list in Settings → Shortcuts.
- The desktop frame has even insets when the sidebar is hidden. Drag notes onto sidebar collections with clear target feedback.
- Desktop empty-state cards use solid adaptive surfaces, so overlapping artwork stays clean in light and dark modes.
- Click anywhere on a task card to open details. Back and Forward follow real navigation history, with unavailable arrows dimmed.
- Kanban cards have a blurred background. Block dragging uses a stable preview and saves the reorder once on release.
- Mac tables support multiline cells, automatic content growth and vertical row resizing alongside column resizing.

## 0.14.2 · Room for what comes next

- Task navigation on Android now blends smoothly with the page and status bar.
- Soft ghost illustrations and useful actions fill empty tasks, notes, images, search, pinned notes, checklists, collections, archive and trash on both apps.
- The update welcome card has richer glass and stronger blur, with no separate background behind the text.
- Notes, slash commands, captions and add-block controls are larger. Your custom text sizes stay as you set them.
- Kanban cards have clear titles, short descriptions and coloured chips. Hold and drag a card on mobile to move it between states.
- Mobile list filters now use an icon and pill button. Note cards and images have softer depth, and notes open with rounded top corners.

## 0.14.1 · A calmer phone interface

- Android home has a richer purple glow with a long falloff and a pale light-mode variant. System bars blend with each page, and redundant navigation labels are removed.
- Library and note headers blur and fade scrolled content while keeping navigation readable. Slash suggestions have distinct icons and comfortable spacing.
- Task boards use subtle neutral surfaces. Task creation groups details, scheduling, description, subtasks and attachments with visible fields and a fixed pill-shaped Save button.
- Settings opens focused pages for Profile, Appearance, Clipboard, Sync, Updates and About, with shorter copy and adaptive colours.

## 0.14.0 · A clearer desktop

- Desktop welcome screens have a seamless background. Settings now includes Profile with a name, bio, photo, and initial avatar.
- Task subtasks have visible fields, tags use translucent colour pills, and image captions reserve space above and below.
- Headings retain their size and weight. Return creates body text, lists continue properly, and slash suggestions format blocks on Mac and Android.
- Block grips align with the first text line. Dragging highlights and moves the whole block, while an in-window menu converts, duplicates, or deletes it.
- Folder rows drag as one group. Tables keep all four corners rounded, and batch actions delete the entire selection.
- Task quick actions offer priority, Today, Tomorrow, and custom scheduling. A faded rolling time picker works on both platforms.

## 0.13.1 · Updates in view

- The Mac update dialog now opens in front when checking or viewing a new release from Settings.
- Verified the Pebble upgrade on a physical Vivo, with existing notes retained and welcome navigation checked.

## 0.13.0 · Hello, Pebble Notes

- Leaf Notes is now Pebble Notes. Your notes, account connection, and preferences stay with you.
- Check for updates in Settings, see every missed changelog, and download verified packages inside the app.
- Android checks periodically for updates and can notify you. Choose Download now, Later, or Install after verification.
- Mac downloads signed updates and offers Install and restart. Updates are distributed through free public GitHub releases.
- A new welcome tour introduces notes, visual inspiration, and tasks. Choose an optional name and accent, then start offline or connect Google Drive.
- Android’s home header has a soft accent glow that fades into the page.

## 0.12.0 · Aligned blocks, focused windows

- Settings navigation blends into the outer frame, scrollbars are thinner and quieter, and task filters sit at the right edge.
- Opening a note in a new window shows only the note and its formatting toolbar.
- Toggle titles align with other writing. Enter creates and focuses nested content; six-dot block handles open options including Delete block.
- Slash suggestions recognise short prefixes and aliases. Return or Tab inserts the selected suggestion on Mac; Android also handles toggle Enter from its software keyboard.

## 0.11.0 · Better blocks, clearer details

- A uniform outer Mac frame, a quieter inset canvas, and clearer Settings materials, controls, slider ticks, and click areas.
- Tasks have grouped properties and scheduling, a rich growing description, editable subtasks, tags, and attachments. Task search filters results and dismisses when clicking empty space.
- Slash commands turn writing into headings, lists, checklists, toggles, tables, quotes, or dividers on Mac; Android offers the same core block commands.
- Nested toggle content collapses and moves as a group. Mac block handles appear on hover; Android shows them for the active block.
- New collections open in a centered blurred dialog, collection icons and names move together, and clipboard suggestions take less space.
- Trash supports permanent deletion and expires items after 30 days. Empty deletion records prevent older synced versions from returning.

## 0.10.0 · A little more space, a little less noise

- A rounded inner Mac canvas with a seamless sidebar edge, a lighter blurred header, and subtle depth on sidebar icons.
- Tasks adapt to narrower windows, highlight across the entire row, and offer spacious icon pills and a custom date calendar.
- Images extend behind floating controls, use smaller neighbouring thumbnails, and support grab-to-pan while zoomed. Mac zoom controls hide after two idle seconds and return on hover.
- Translucent Mac Settings and collection confirmation, an About page, and a contextual cloud sync indicator beside Settings.
- Compact theme controls and a choice of Yellow, Blue, Purple, Pink, Green, or Orange accents in Settings on both devices.
- Collection dragging leaves an empty gap. Clicking empty library space clears card selection.
- Android has a dedicated Settings page, task and Settings shortcuts, simpler theme controls, more room above system navigation, and hold-for-options in both list and gallery views.
- Android image zoom controls stay accessible above the bottom safe area. Clipboard previews no longer open stale file-provider URLs on Mac.

## 0.9.0 · More room to move

- A lighter blurred header, full circular menu buttons, and app-centered Settings.
- Drag collections and note items to reorder them. Resize the Mac sidebar and table columns, and drag from empty library space to select cards.
- Tasks now offer List and Board views with To Do, In Progress, In Review, and Done statuses.
- Images move with a subtle spring, zoom as a whole, and use the same navigation capsule as the library.
- Clipboard suggestions dismiss after five seconds. Collection deletion asks for confirmation and keeps your notes.
- Subtle Android touch feedback for navigation, formatting, segmented controls, and writing sliders.
- Slightly smaller default note typography, while keeping your custom text sizes.

## 0.8.0 · A little news with every update

- What’s New now appears once after an update, so you can see what changed.
- A translucent release panel keeps the app softly visible behind it.
- Reopen the changelog from Settings whenever you like.

## 0.7.0 · A calmer place to write

- Smaller default note text, with your custom text sizes preserved.
- A blurred translucent header replaces progressive fades as content scrolls underneath.
- Mac forms and image viewers stay inside the main content pane beside the sidebar.
- Sidebar hover brightens the text and icons without adding extra boxes.

## 0.6.0 · Keep thoughts, images, and plans together

- Tasks with dates, priorities, subtasks, repeating schedules, and optional notifications.
- Image browsing with animated neighbouring thumbnails, editable captions, and glass zoom controls.
- Refined card dragging, simpler Settings, and the updated Leaf icon.
