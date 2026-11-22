"""Original wing geometry: exports the same cuboids to Java and editable Blockbench."""
import json,uuid,math,base64
from pathlib import Path
root=Path(__file__).resolve().parents[1]
elements=[];groups=[];lines=[]
def uid():return str(uuid.uuid5(uuid.NAMESPACE_URL,'zorykai:'+str(len(elements))+':'+str(len(groups)) ))
def feather(name,x,y,z,width,length,roll,layer):
    # Segments meet edge-to-edge: no overlapping coplanar faces inside a feather.
    boxes=[(0,0,0,width,length*.70,1.0),(width*.12,length*.70,0,width*.76,length*.20,1.0),(width*.30,length*.90,0,width*.40,length*.10,1.0)]
    for side in [1,-1]:
        n=name+('_left' if side==1 else '_right');origin=[side*(2+x),22-y,3+z];children=[]
        parts=[]
        for bx,by,bz,w,h,d in boxes:
            xx=bx if side==1 else -bx-w
            parts.append(f'.uv({0 if layer==0 else 64},0).cuboid({xx:.4f}f,{by:.4f}f,{bz:.4f}f,{w:.4f}f,{h:.4f}f,{d:.4f}f)')
            u=uid();children.append(u)
            elements.append({'name':n,'uuid':u,'type':'cube','from':[origin[0]+xx,origin[1]-by-h,origin[2]+bz],'to':[origin[0]+xx+w,origin[1]-by,origin[2]+bz+d],'origin':origin,'rotation':[0,0,0],'box_uv':True,'uv_offset':[0 if layer==0 else 64,0],'faces':{face:{'uv':[0,0,4,28],'texture':0} for face in ['north','south','east','west','up','down']}})
        groups.append({'name':n,'origin':origin,'rotation':[0,0,-side*roll*180/math.pi],'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,n)),'children':children})
        lines.append(f'        {"left" if side==1 else "right"}.addChild("{name}",ModelPartBuilder.create(){"".join(parts)},ModelTransform.of({side*x:.4f}f,{y:.4f}f,{z:.4f}f,0,0,{side*roll:.4f}f));')
# One pair only: continuous primary feathers, no coverts or extra shoulder pieces.
for i,(x,y,l,r) in enumerate([(0,1,12,.10),(1.6,0,14,.10),(3.2,-1,16,.10),(4.8,-1.6,18,.10),(6.4,-1,19.5,.10),(8,.2,19,.10)]):
    feather('primary_'+str(i),x,y,.2+i*.24,3.4,l,r,0)
java='''package dev.zorykai.cosmetics.client;
import net.minecraft.client.model.*;
/** Generated from tools/generate_angel.py; matching editable asset: models/angel.bbmodel. */
final class AngelGeometry {
    static ModelPart create() {
        var data=new ModelData();var root=data.getRoot();
        var left=root.addChild("left",ModelPartBuilder.create(),ModelTransform.pivot(2,2,3));
        var right=root.addChild("right",ModelPartBuilder.create(),ModelTransform.pivot(-2,2,3));
'''+ '\n'.join(lines)+'''
        return TexturedModelData.of(data,128,64).createModel();
    }
}
'''
(root/'src/main/java/dev/zorykai/cosmetics/client/AngelGeometry.java').write_text(java)
texture=root/'src/main/resources/assets/zorykai_cosmetics/textures/wings.png'
(root/'models/angel.bbmodel').write_text(json.dumps({'meta':{'format_version':'4.10','model_format':'free','box_uv':True},'name':'Zorykai Angel','model_identifier':'zorykai_angel','resolution':{'width':128,'height':64},'elements':elements,'outliner':groups,'textures':[{'path':'','name':'wings.png','id':'0','width':128,'height':64,'uv_width':128,'uv_height':64,'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}]},indent=2))
