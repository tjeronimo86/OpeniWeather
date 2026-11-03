// Rendert sky.agsl im Browser: wandelt die wenigen AGSL-Besonderheiten nach GLSL ES um.
export function compileSky(canvas, src) {
  const gl = canvas.getContext('webgl', {antialias:false, preserveDrawingBuffer:true});
  if (!gl) throw new Error('kein WebGL');
  const frag = 'precision highp float;\n#define half4 vec4\n#define half3 vec3\n' +
    src.replace('half4 main(vec2 frag) {', 'vec4 skyMain(vec2 frag) {') +
    '\nvoid main(){ gl_FragColor = skyMain(vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y)); }';
  const sh = (t, s) => { const o = gl.createShader(t); gl.shaderSource(o, s); gl.compileShader(o);
    if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o)); return o; };
  const p = gl.createProgram();
  gl.attachShader(p, sh(gl.VERTEX_SHADER, 'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}'));
  gl.attachShader(p, sh(gl.FRAGMENT_SHADER, frag));
  gl.linkProgram(p); gl.useProgram(p);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,3,-1,-1,3]), gl.STATIC_DRAW);
  const a = gl.getAttribLocation(p,'a'); gl.enableVertexAttribArray(a); gl.vertexAttribPointer(a,2,gl.FLOAT,false,0,0);
  const u = n => gl.getUniformLocation(p,n);
  return (scene, t) => {
    gl.viewport(0,0,canvas.width,canvas.height);
    gl.uniform2f(u('uRes'),canvas.width,canvas.height); gl.uniform1f(u('uTime'),t); gl.uniform1f(u('uCloudTime'),t); gl.uniform1f(u('uSunVis'),1);
    for (const [k,v] of Object.entries(scene)) Array.isArray(v) ? gl.uniform2f(u(k),v[0],v[1]) : gl.uniform1f(u(k),v);
    gl.drawArrays(gl.TRIANGLES,0,3);
  };
}
