import importlib.util
import tempfile
import unittest
from pathlib import Path

spec=importlib.util.spec_from_file_location('import_markdown',Path(__file__).parents[1]/'scripts/import-markdown.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class MarkdownImportTests(unittest.TestCase):
    def test_semantic_blocks_and_unicode_spans(self):
        source='# My note\n\n## Heading\n\n- [ ] Buy **milk**\n- [x] Done 👋 *today*\n  - Child\n\n1. First\n2. Second\n\nA [link](https://example.com) and ~~old~~.\n'
        note,_,missing=module.convert(source,'Fallback','Imported')
        self.assertEqual(note['title'],'My note');self.assertEqual(missing,0)
        blocks=note['blocks'];self.assertEqual(blocks[0]['textStyle'],'subtitle')
        self.assertEqual([(b['kind'],b['checked']) for b in blocks[1:3]],[('check',False),('check',True)])
        self.assertEqual(blocks[3]['indent'],1)
        self.assertEqual(blocks[2]['spans'][0],dict(start=8,length=5,kind='italic'))
        self.assertIn('link:https://example.com',[s['kind'] for s in blocks[-1]['spans']])
    def test_tables_literal_code_and_missing_images(self):
        source='# Note\n\n| A | B |\n| --- | --- |\n| one | two |\n\n```\n- [ ] literal\n```\n\n![Photo](missing.jpeg)\n'
        note,media,missing=module.convert(source,'Fallback','Imported')
        self.assertEqual(note['blocks'][0]['cells'],[['A','B'],['one','two']])
        self.assertEqual(note['blocks'][1]['text'],'- [ ] literal')
        self.assertEqual(missing,1);self.assertEqual(media,{})
        self.assertIn('missing.jpeg',note['text'])
    def test_apple_indented_and_empty_checklists(self):
        note,_,_=module.convert("# Note\n\n    - [ ] One\n    - [x] Two\n\n- [ ]\n", "Fallback", "Imported")
        self.assertEqual([b["kind"] for b in note["blocks"]],["check","check","check"])
        self.assertEqual([b["checked"] for b in note["blocks"]],[False,True,False])

    def test_repeat_conversion_is_identical_and_no_fetch(self):
        with tempfile.TemporaryDirectory() as directory:
            folder=Path(directory);(folder/'Note.md').write_text('# Title\n\n![Remote](https://example.com/a.jpg)\n')
            first,stats=module.archive(folder);second,_=module.archive(folder)
            self.assertEqual(first,second);self.assertEqual(stats['notes'],1)
            self.assertEqual(stats['missing_images'],1);self.assertEqual(first['media'],{})
    def test_local_media_cannot_escape_export_folder(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);folder=root/'export';folder.mkdir();(root/'secret.png').write_bytes(b'private')
            (folder/'Note.md').write_text('# Note\n\n![Photo](../secret.png)\n')
            result,stats=module.archive(folder)
            self.assertEqual(result['media'],{});self.assertEqual(stats['missing_images'],1)

if __name__=='__main__':unittest.main()
