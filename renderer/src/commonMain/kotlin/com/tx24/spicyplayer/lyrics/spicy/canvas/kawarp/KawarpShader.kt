package com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp

/**
 * 1:1 port of @kawarp/core 1.2.0 (MIT), with the options in `SPICY_OPTIONS`.
 *
 * Image-change path (KawarpBlurCore): tint → 8 Kawase passes at 128×128, stored as
 * RGBA_F16 "album" bitmaps (half-float FBO parity). Per-frame path: one fused AGSL
 * shader = the original's BLEND + DOMAIN_WARP + OUTPUT passes, PLUS the
 * `saturate(2.5) brightness(0.65)` filter over the whole canvas — composed in one shader since blend/warp/output are
 * pure functions of uv and the CSS filter is just a final per-pixel color transform.
 */
const val KAWARP_FUSED_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float uBlend;
uniform float uIntensity;
uniform float uSaturation;
uniform float uDithering;
uniform float uScale;
uniform shader texCur;
uniform shader texNext;

const float BLUR_SIZE = 128.0;
// The reference never sizes its canvas, so WebGL draws into the default 300x150 backbuffer
// and CSS stretches it over the page.
const float2 CANVAS_SIZE = float2(300.0, 150.0);

float3 mod289v3(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
float2 mod289v2(float2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
float3 permute(float3 x) { return mod289v3(((x * 34.0) + 1.0) * x); }

float snoise(float2 v) {
    const float4 C = float4(0.211324865405187, 0.366025403784439,
                            -0.577350269189626, 0.024390243902439);
    float2 i  = floor(v + dot(v, C.yy));
    float2 x0 = v - i + dot(i, C.xx);
    float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod289v2(i);
    float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
    float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m; m = m * m;
    float3 x = 2.0 * fract(p * C.www) - 1.0;
    float3 h = abs(x) - 0.5;
    float3 ox = floor(x + 0.5);
    float3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    float3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.y = a0.y * x12.x + h.y * x12.y;
    g.z = a0.z * x12.z + h.z * x12.w;
    return 130.0 * dot(m, g);
}

float hash(float3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

half4 main(float2 fragCoord) {
    // WebGL texcoords start at the bottom-left, and the cover is uploaded without flipping,
    // so it shows upside down (and the warp drifts accordingly), as in a browser. Same here.
    float2 vTexCoord = float2(fragCoord.x / uResolution.x, 1.0 - fragCoord.y / uResolution.y);

    // OUTPUT pass uv-scale, applied up front (warp is pure in uv, so order commutes)
    float2 uv = clamp((vTexCoord - 0.5) / uScale + 0.5, 0.0, 1.0);

    // DOMAIN_WARP pass
    float t = uTime * 0.05;
    float2 center = uv - 0.5;
    float centerWeight = 1.0 - smoothstep(0.0, 0.7, length(center));
    float n1 = snoise(uv * 0.35 + float2(t, t * 0.7));
    float n2 = snoise(uv * 0.35 + float2(-t * 0.8, t * 0.5) + float2(50.0, 50.0));
    float n3 = snoise(uv * 0.9 + float2(t * 1.2, -t) + float2(100.0, 0.0));
    float n4 = snoise(uv * 0.9 + float2(-t, t * 1.1) + float2(0.0, 100.0));
    float2 warp = float2(n1 * 0.65 + n3 * 0.35, n2 * 0.65 + n4 * 0.35) * centerWeight;
    float2 warpedUV = clamp(uv + warp * uIntensity, 0.0, 1.0);

    // BLEND pass, fused: sample both album textures at the warped coordinate.
    // GL texel centers are at (i+0.5)/N; eval coords map [0,1] → [0,BLUR_SIZE].
    float2 texPos = warpedUV * BLUR_SIZE;
    half4 c1 = texCur.eval(texPos);
    half4 c2 = texNext.eval(texPos);
    half4 color = mix(c1, c2, half(uBlend));

    // OUTPUT pass: vignette, saturation, dithering (in canvas texcoord space)
    float2 vc = vTexCoord - 0.5;
    float vignette = 1.0 - dot(vc, vc) * 0.3;
    color.rgb *= half(vignette);
    half gray = dot(color.rgb, half3(0.299, 0.587, 0.114));
    color.rgb = mix(half3(gray), color.rgb, half(uSaturation));
    // Dither per backbuffer pixel, then bilinearly stretched like the browser's upscale.
    float frame = floor(uTime * 60.0);
    float2 grid = vTexCoord * CANVAS_SIZE - 0.5;
    float2 cell = floor(grid);
    float2 f = grid - cell;
    float2 lo = clamp(cell, float2(0.0), CANVAS_SIZE - 1.0);
    float2 hi = clamp(cell + 1.0, float2(0.0), CANVAS_SIZE - 1.0);
    float noise = mix(
        mix(hash(float3(lo.x, lo.y, frame)), hash(float3(hi.x, lo.y, frame)), f.x),
        mix(hash(float3(lo.x, hi.y, frame)), hash(float3(hi.x, hi.y, frame)), f.x),
        f.y);
    color.rgb += half3(half((noise - 0.5) * uDithering));
    // WebGL's default canvas backbuffer is 8-bit UNORM: this is where the browser
    // clamps before CSS ever sees the pixels, same as here.
    color.rgb = clamp(color.rgb, half3(0.0), half3(1.0));

    // `filter: saturate(2.5) brightness(0.65)` over the whole canvas, on top of the shader's own
    // internal saturation/vignette/dither above. CSS saturate() is a luma-preserving mix
    // using Rec.709-ish weights (0.213/0.715/0.072), distinct from the shader's own 0.299/
    // 0.587/0.114 (Rec.601) — kept separate to match the spec exactly. Chained CSS filter
    // primitives each rasterize to 8-bit before the next runs, so clamp between them too —
    // skipping this let saturate(2.5) push channels past 1.0 where brightness(0.65)
    // could no longer pull them back down, which is why the port looked too bright.
    half cssGray = dot(color.rgb, half3(0.213, 0.715, 0.072));
    color.rgb = mix(half3(cssGray), color.rgb, half(2.5));
    color.rgb = clamp(color.rgb, half3(0.0), half3(1.0));
    color.rgb *= half(0.65);

    return color;
}
"""
