/* SPDX-License-Identifier: Apache-2.0 */
'use strict';
const PHOTO_FORMATS={feed:[1080,1350],square:[1080,1080],story:[1080,1920]};
const PHOTO_MAX_BYTES=12*1024*1024;
let photoImage=null,photoObjectUrl=null,photoVersion=0,photoRenderVersion=0,photoExportPending=null,photoExportTimeout=null,preparedPhoto=null;
let photoLogo=null;
function photoMessage(text){document.getElementById('photoMessage').textContent=text;}
function photoRoundRect(ctx,x,y,width,height,radius){
    ctx.beginPath();ctx.moveTo(x+radius,y);ctx.arcTo(x+width,y,x+width,y+height,radius);ctx.arcTo(x+width,y+height,x,y+height,radius);ctx.arcTo(x,y+height,x,y,radius);ctx.arcTo(x,y,x+width,y,radius);ctx.closePath();
}
function photoCaptionLines(ctx,text,maxWidth){
    const words=text.trim().split(/\s+/).filter(Boolean),lines=[];let line='';
    for(const word of words){
        if(ctx.measureText(word).width>maxWidth){
            if(line){lines.push(line);line='';}
            let piece='';for(const char of word){if(ctx.measureText(piece+char).width>maxWidth){lines.push(piece);piece=char;}else piece+=char;}line=piece;
        }else if(ctx.measureText(line?line+' '+word:word).width<=maxWidth)line=line?line+' '+word:word;
        else{lines.push(line);line=word;}
    }
    if(line)lines.push(line);return lines;
}
async function drawPhoto(){
    const version=++photoRenderVersion;
    const canvas=document.getElementById('photoCanvas'),ctx=canvas.getContext('2d');
    const [width,height]=PHOTO_FORMATS[document.getElementById('photoFormat').value]||PHOTO_FORMATS.feed;
    canvas.width=width;canvas.height=height;
    const light=document.getElementById('photoStyle').value==='light';
    ctx.fillStyle=light?'#eff7fa':'#071b2a';ctx.fillRect(0,0,width,height);
    const caption=document.getElementById('photoCaption').value.trim();
    ctx.font='600 40px sans-serif';
    let lines=photoCaptionLines(ctx,caption,width-112);
    while(lines.length>3 && ctx.measureText(lines[2]+'…').width>width-112)lines[2]=lines[2].slice(0,-1);
    if(lines.length>3)lines=[lines[0],lines[1],lines[2]+'…'];
    const footer=172+(lines.length?lines.length*50+22:0),box={x:32,y:32,w:width-64,h:height-footer-48};
    ctx.save();photoRoundRect(ctx,box.x,box.y,box.w,box.h,26);ctx.clip();
    ctx.fillStyle=light?'#dcebf1':'#123548';ctx.fillRect(box.x,box.y,box.w,box.h);
    if(photoImage){
        const scale=Math.min(box.w/photoImage.width,box.h/photoImage.height),w=photoImage.width*scale,h=photoImage.height*scale;
        ctx.drawImage(photoImage,box.x+(box.w-w)/2,box.y+(box.h-h)/2,w,h);
    }else{
        ctx.fillStyle=light?'#476575':'#a7c0cc';ctx.font='500 36px sans-serif';ctx.textAlign='center';ctx.fillText('Escolha uma foto da sua natação',width/2,box.y+box.h/2);ctx.textAlign='left';
    }
    ctx.restore();
    const captionY=box.y+box.h+62;ctx.fillStyle=light?'#102c3c':'#eef8fb';ctx.font='600 40px sans-serif';
    lines.forEach((line,index)=>ctx.fillText(line,56,captionY+index*50));
    const logoY=height-122;
    if(!photoLogo){
        try{photoLogo=await new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve(image);image.onerror=reject;image.src='icon-192.png';});}catch(error){photoLogo=null;}
    }
    if(version!==photoRenderVersion)return;
    if(photoLogo)ctx.drawImage(photoLogo,56,logoY,80,80);
    else{ctx.fillStyle='#61e3d1';ctx.font='700 70px sans-serif';ctx.fillText('≈',65,logoY+61);}
    ctx.fillStyle=light?'#102c3c':'#eef8fb';ctx.font='700 36px sans-serif';ctx.fillText('Nado no meu ritmo',160,logoY+33);
    ctx.fillStyle=light?'#476575':'#a7c0cc';ctx.font='500 24px sans-serif';ctx.fillText('Um treino de cada vez.',160,logoY+68);
    canvas.setAttribute('aria-label','Prévia da foto com a marca Nado no meu ritmo, '+width+' por '+height+' pixels');
}
async function choosePhoto(file){
    const version=++photoVersion;
    if(!file)return;
    if(!file.type.startsWith('image/') || file.size>25*1024*1024){photoMessage('Escolha uma imagem de até 25 MB. JPEG, PNG e WebP são os formatos mais indicados.');return;}
    try{
        const url=URL.createObjectURL(file);
        let image;
        try{image=await new Promise((resolve,reject)=>{const loaded=new Image();loaded.onload=()=>resolve(loaded);loaded.onerror=reject;loaded.src=url;});}catch(error){URL.revokeObjectURL(url);throw error;}
        if(version!==photoVersion){URL.revokeObjectURL(url);return;}
        if(!image.naturalWidth || !image.naturalHeight || image.naturalWidth*image.naturalHeight>60000000){URL.revokeObjectURL(url);throw new Error('Foto muito grande. Use uma cópia com resolução menor.');}
        const scale=Math.min(1,2400/Math.max(image.naturalWidth,image.naturalHeight));
        const resized=document.createElement('canvas');resized.width=Math.max(1,Math.round(image.naturalWidth*scale));resized.height=Math.max(1,Math.round(image.naturalHeight*scale));
        resized.getContext('2d').drawImage(image,0,0,resized.width,resized.height);
        if(photoObjectUrl)URL.revokeObjectURL(photoObjectUrl);photoObjectUrl=url;photoImage=resized;
        await drawPhoto();
        document.getElementById('photoSave').disabled=false;document.getElementById('photoShare').disabled=false;
        photoMessage('Foto pronta para edição. O enquadramento completo é preservado e o arquivo original não é alterado.');
    }catch(error){photoMessage(error.message||'Não foi possível abrir essa foto. Tente uma cópia em JPEG, PNG ou WebP.');}
}
function photoBusy(busy){
    document.querySelectorAll('#photoDialog input,#photoDialog select,#photoSave,#photoShare').forEach(element=>element.disabled=busy || (!photoImage && ['photoSave','photoShare'].includes(element.id)));
}
async function photoBlob(){
    if(!photoImage)throw new Error('Escolha uma foto primeiro.');
    await drawPhoto();
    const blob=await new Promise((resolve,reject)=>document.getElementById('photoCanvas').toBlob(value=>value?resolve(value):reject(new Error('Não foi possível gerar a imagem.')),'image/png'));
    if(blob.size>PHOTO_MAX_BYTES)throw new Error('A imagem gerada ficou grande demais. Escolha outra foto ou o formato quadrado.');
    return blob;
}
function clearPreparedPhoto(){preparedPhoto=null;clearTimeout(photoExportTimeout);photoExportPending=null;photoBusy(false);}
window.photoExportInfo=id=>preparedPhoto && preparedPhoto.id===id?{length:preparedPhoto.base64.length}:null;
window.photoExportChunk=(id,offset,length)=>preparedPhoto && preparedPhoto.id===id && Number.isInteger(offset) && offset>=0 && length>0 && length<=65536?preparedPhoto.base64.slice(offset,offset+length):null;
window.receivePhotoExport=(id,result)=>{
    if(!photoExportPending || id!==photoExportPending)return;
    clearPreparedPhoto();photoMessage(String(result.message||'Operação concluída.').slice(0,500));
};
async function exportPhoto(action){
    if(photoExportPending)return;
    photoBusy(true);
    try{
        const blob=await photoBlob();
        if(!document.getElementById('photoDialog').open){photoBusy(false);return;}
        if(isAndroidApp() && window.nadoNativeReady){
            const base64=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result).split(',')[1]);reader.onerror=reject;reader.readAsDataURL(blob);});
            const id=crypto.randomUUID();preparedPhoto={id,base64};photoExportPending=id;
            photoExportTimeout=setTimeout(()=>{if(photoExportPending){window.location.href='nado://photo?action=cancel&id='+id;clearPreparedPhoto();photoMessage('A operação demorou demais. Tente salvar ou compartilhar novamente.');}},120000);
            photoMessage(action==='share'?'Preparando a foto para escolher uma rede social…':'Preparando a foto para escolher onde salvar…');
            window.location.href='nado://photo?action='+action+'&id='+id;
            return;
        }
        const file=new File([blob],'nado-minha-natacao.png',{type:'image/png'});
        if(action==='share' && navigator.canShare?.({files:[file]})){
            try{await navigator.share({files:[file],title:'Minha natação · Nado no meu ritmo'});photoMessage('Compartilhamento solicitado ao aplicativo escolhido.');}
            catch(error){if(error.name==='AbortError')photoMessage('Compartilhamento cancelado.');else throw error;}
        }else{
            const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=file.name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
            photoMessage(action==='share'?'Imagem baixada. Abra a rede social e selecione o arquivo para publicar.':'Imagem PNG baixada.');
        }
    }catch(error){photoMessage(error.message||'Não foi possível preparar a imagem.');}
    if(!photoExportPending)photoBusy(false);
}
function setupPhotoStudio(){
    const button=document.createElement('button');button.id='photoStudioBtn';button.className='btn btn-secondary';button.type='button';button.textContent='Foto para compartilhar';
    document.querySelector('.hero .actions').append(button);
    const dialog=document.createElement('dialog');dialog.id='photoDialog';dialog.className='photo-dialog';dialog.setAttribute('aria-labelledby','photoTitle');
    dialog.innerHTML=`<div class="modal-head"><h2 id="photoTitle">Minha natação em foto</h2><button id="photoClose" class="btn btn-quiet" type="button">Fechar</button></div>
      <p class="sub">Escolha uma foto, personalize e leve a marca do aplicativo para suas redes. A foto fica no aparelho; nada é publicado automaticamente.</p>
      <div class="photo-controls"><div class="field"><label for="photoFile">Escolher foto</label><input id="photoFile" type="file" accept="image/*"></div>
      <div class="field"><label for="photoFormat">Formato</label><select id="photoFormat"><option value="feed">Instagram · 4:5 (1080 × 1350)</option><option value="square">Quadrado · 1:1 (1080 × 1080)</option><option value="story">Stories · 9:16 (1080 × 1920)</option></select></div>
      <div class="field"><label for="photoStyle">Moldura da foto</label><select id="photoStyle"><option value="dark">Escura</option><option value="light">Clara</option></select></div>
      <div class="field"><label for="photoCaption">Texto na imagem (opcional)</label><input id="photoCaption" maxlength="100" placeholder="Escreva do seu jeito"></div></div>
      <div class="photo-preview"><canvas id="photoCanvas" width="1080" height="1350" role="img" aria-label="Prévia da foto"></canvas></div>
      <div class="actions"><button id="photoSave" class="btn btn-primary" type="button" disabled>Salvar foto com logo</button><button id="photoShare" class="btn btn-secondary" type="button" disabled>Compartilhar nas redes</button></div>
      <p id="photoMessage" class="sub" role="status" aria-live="polite">Nenhuma foto selecionada. As fotos não entram no backup do diário.</p>`;
    document.body.append(dialog);
    button.onclick=()=>{dialog.showModal();void drawPhoto();};
    document.getElementById('photoClose').onclick=()=>dialog.close();
    document.getElementById('photoFile').onchange=event=>void choosePhoto(event.target.files[0]);
    for(const id of ['photoFormat','photoStyle','photoCaption'])document.getElementById(id).addEventListener('input',()=>void drawPhoto());
    document.getElementById('photoSave').onclick=()=>void exportPhoto('save');
    document.getElementById('photoShare').onclick=()=>void exportPhoto('share');
    dialog.addEventListener('close',()=>{
        photoVersion++;photoRenderVersion++;
        if(photoExportPending && window.nadoNativeReady)window.location.href='nado://photo?action=cancel&id='+photoExportPending;
        clearPreparedPhoto();photoImage=null;if(photoObjectUrl)URL.revokeObjectURL(photoObjectUrl);photoObjectUrl=null;photoBusy(false);
        document.getElementById('photoFile').value='';document.getElementById('photoCaption').value='';
        photoMessage('Nenhuma foto selecionada. As fotos não entram no backup do diário.');
        void drawPhoto();
    });
}
setupPhotoStudio();
