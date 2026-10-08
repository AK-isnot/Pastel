import * as THREE from 'three';

import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';

const WIDTH = 600;
const HEIGHT = window.innerHeight;

// レンダラー
const canvas = document.getElementById("pastel-canvas");
const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
renderer.setSize(WIDTH, HEIGHT);
renderer.setPixelRatio(window.devicePixelRatio);


// camera
const camera = new THREE.PerspectiveCamera(30.0, WIDTH / HEIGHT, 0.1, 20.0);
// cameraの初期位置を決める（左右、上下、奥行き）回転してるのでちょっとむずい
camera.position.set(0.0, 1.5, 1.0);

// camera controls
const controls = new OrbitControls(camera, renderer.domElement);
controls.screenSpacePanning = true;
//カメラが狙う対象、位置をきめる
controls.target.set(0.0, 1.4, 0.0);
controls.update();

// scene
const scene = new THREE.Scene();

// light
const light = new THREE.DirectionalLight(0xffffff, Math.PI);
light.position.set(1.0, 1.0, 1.0).normalize();
scene.add(light);


// gltf and vrm
let currentVrm = undefined;
const loader = new GLTFLoader();
loader.crossOrigin = 'anonymous';

loader.register((parser) => {

    return new VRMLoaderPlugin(parser);

});

loader.load(

    // ぱすてるの読み込み
    './model/pastel_0.0.1.vrm',

    // ぱすてるが読み込まれているときに呼ばれる
    (gltf) => {

        const vrm = gltf.userData.vrm;

        // calling these functions greatly improves the performance
        VRMUtils.removeUnnecessaryVertices(gltf.scene);
        VRMUtils.combineSkeletons(gltf.scene);
        VRMUtils.combineMorphs(vrm);

        // Disable frustum culling
        vrm.scene.traverse((obj) => {

            obj.frustumCulled = false;

        });

        currentVrm = vrm;

        //左腕の操作
        vrm.humanoid.getNormalizedBoneNode('leftUpperArm').rotation.z = -1.1;

        //右腕の操作
        vrm.humanoid.getNormalizedBoneNode('rightUpperArm').rotation.z = 1.1;


        scene.add(vrm.scene);

    },

    // called while loading is progressing
    (progress) => console.log('Loading model...', 100.0 * (progress.loaded / progress.total), '%'),

    // called when loading has errors
    (error) => console.error(error)

);

// animate
const clock = new THREE.Clock();
clock.start();

let timeUntilBlink = 3;   // 次のまばたきまでの残り秒
let blinkRemaining = 0;   // 目を閉じている残り秒

let breathProgress = 0;   // 今の呼吸がどこまで進んだか（0〜1）
let breathSeconds = 4;    // 今の呼吸の長さ（秒）

function animate() {

    requestAnimationFrame(animate);
    const deltaTime = clock.getDelta();

    // update vrm components
    if (currentVrm) {

        //呼吸
        //今の状態を前のフレームから経った分だけ進める
        breathProgress += deltaTime / breathSeconds;
        //１回の呼吸が終わったら次の呼吸の長さを決め直す
        if (breathProgress >= 1) {
            breathProgress -= 1;
            breathSeconds = 3 + Math.random() * 2;
        }
        //胸のボーンを取ってうごかす
        const chest = currentVrm.humanoid.getNormalizedBoneNode("chest");
        chest.rotation.x = Math.sin(breathProgress * Math.PI * 2) * 0.02;

        //瞬き
        // 両方の残り秒から、経過秒を引く
        timeUntilBlink -= deltaTime;
        blinkRemaining -= deltaTime;
        // 次のまばたきの時間が来たら、目を閉じて、次の間隔を決め直す
        if (timeUntilBlink <= 0) {
            blinkRemaining = 0.1;
            timeUntilBlink = 2 + Math.random() * 4;
        }
        // 閉じている残りがあれば1（閉じる）、なければ0（開く）
        currentVrm.expressionManager.setValue('blink', blinkRemaining > 0 ? 1 : 0);

        //モデルに反映する処理
        currentVrm.update(deltaTime);
    }

    // render
    renderer.render(scene, camera);

}

animate();