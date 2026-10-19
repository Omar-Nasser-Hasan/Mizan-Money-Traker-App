# P15 release gallery

Captures come from real Room-backed Compose screens on the API 37 Pixel_7_Pro emulator, using software rendering after the embedded emulator graphics stalled. Dark screenshots use a 400 × 760 dp content viewport at 1.3x text. They show the scroll position used to expose the relevant form action or sheet; they are not whole-page composites. Captures use Compose's window/PixelCopy path for the current root; standalone screen captures do not add outer shell controls.

The fixture includes an Arabic cash account/payee/note, a credit card, an expense, a lent debt, a gold purchase and price, a budget, planned payment, goal, Inbox draft and income split. Expected Home/Wealth net worth is EGP 695.00: cash EGP 605.00 + gold EGP 60.00 + receivable EGP 30.00. Cash-account balance is EGP 705.00 and card balance is -EGP 100.00.

P15QualityInstrumentedTest also renders all 55 screens/modals in light landscape (900 × 400 dp) and true-black tablet (840 × 1050 dp), each at 1.3x. The full matrix passes as a layout/crash/semantics gate; it does not replace physical TalkBack or clipping review. The gallery pairs these app captures with the supplied Stitch PNGs in ../README.md. Earlier first-run screenshots remain P12 evidence; P1 screenshots were not rechecked.

See ../../P15_ACCEPTANCE.md for coverage, ../README.md for the gallery, and ../../../PHONE_CHECKLIST.md for device-only acceptance.
