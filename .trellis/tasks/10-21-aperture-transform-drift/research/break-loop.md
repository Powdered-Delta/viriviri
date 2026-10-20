# Bug analysis: repeated aperture drift failures

1. Category: D (test coverage gap) and E (implicit assumption); final visual root remains unresolved. Controller/SDK math tests validate submitted values, not rendered geometry.
2. Why fixes failed: aspect repair correctly fixed textures; group scaling fixed similarity semantics but had no evidence tying it to the visible drifting window. Shared object/config identity was treated as stronger evidence than it is.
3. Prevention: distinguish raw inputs, derived geometry, submitted pose, native mesh collision point and compositor pixels. Diagnostic build makes only the existing video mesh measurable, with explicit missing/blocked results. Full-frame texture acceptance remains protected.
4. Expansion: native Bounds and API Pose are different coordinate spaces; never infer compensation sign from their raw logged Z values. A separate native node/render transform may still differ from collision evidence.
5. Knowledge capture: geometry code-spec and rendering specification now document these evidence boundaries and opt-in measurement contract. Root task remains open until actual headset samples support a fix.
