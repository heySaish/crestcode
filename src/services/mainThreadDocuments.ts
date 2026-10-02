export class MainThreadDocumentsImpl {
  private openDocuments = new Map<string, string>();

  $acceptModelContentChanged(uri: string, text: string, version: number) {
    console.log(`\n📄 [CREST DOCUMENTS]: Text changed in ${uri} (v${version})`);
    this.openDocuments.set(uri, text);
  }

  $acceptModelOpened(uri: string, text: string) {
    console.log(`\n📄 [CREST DOCUMENTS]: Document Opened: ${uri}`);
    this.openDocuments.set(uri, text);
  }

  $acceptModelSaved(uri: string) {
    console.log(`\n📄 [CREST DOCUMENTS]: Document Saved: ${uri}`);
  }

  $acceptModelClosed(uri: string) {
    console.log(`\n📄 [CREST DOCUMENTS]: Document Closed: ${uri}`);
    this.openDocuments.delete(uri);
  }
}
