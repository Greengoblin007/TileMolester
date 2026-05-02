/*
*
*    Per-file view settings (codec, mode, zoom, canvas size,
*    last externally-imported palette). Persisted alongside
*    bookmarks/palettes in the resources XML, applied on open,
*    captured on close.
*
*/

package tm;

import tm.colorcodecs.ColorCodec;
import tm.tilecodecs.TileCodec;
import tm.ui.TMUI;
import tm.ui.TMView;
import org.w3c.dom.Element;

import java.io.File;
import java.io.RandomAccessFile;

public class ViewSettings {

	private String codecID;
	private Integer mode;
	private Double zoom;
	private Integer cols;
	private Integer rows;
	private Integer offset;

	private boolean hasExternalPalette;
	private String palettePath;
	private int paletteOffset;
	private int paletteSize;
	private String paletteCodecID;
	private int paletteEndianness;

	public ViewSettings() {}

	public static ViewSettings parse(Element root) {
		Element e = (Element) root.getElementsByTagName("viewsettings").item(0);
		if (e == null) return null;

		ViewSettings vs = new ViewSettings();
		if (e.hasAttribute("codec")) {
			vs.codecID = e.getAttribute("codec");
		}
		if (e.hasAttribute("mode")) {
			vs.mode = e.getAttribute("mode").equals("2D") ? TileCodec.MODE_2D : TileCodec.MODE_1D;
		}
		if (e.hasAttribute("zoom")) {
			try { vs.zoom = Double.parseDouble(e.getAttribute("zoom")); }
			catch (NumberFormatException ignored) {}
		}
		if (e.hasAttribute("cols")) {
			try { vs.cols = Integer.parseInt(e.getAttribute("cols")); }
			catch (NumberFormatException ignored) {}
		}
		if (e.hasAttribute("rows")) {
			try { vs.rows = Integer.parseInt(e.getAttribute("rows")); }
			catch (NumberFormatException ignored) {}
		}
		if (e.hasAttribute("offset")) {
			try { vs.offset = Integer.parseInt(e.getAttribute("offset")); }
			catch (NumberFormatException ignored) {}
		}

		Element ep = (Element) e.getElementsByTagName("externalpalette").item(0);
		if (ep != null && ep.hasAttribute("path") && ep.hasAttribute("offset")
				&& ep.hasAttribute("size") && ep.hasAttribute("codec")) {
			try {
				vs.palettePath = ep.getAttribute("path");
				vs.paletteOffset = Integer.parseInt(ep.getAttribute("offset"));
				vs.paletteSize = Integer.parseInt(ep.getAttribute("size"));
				vs.paletteCodecID = ep.getAttribute("codec");
				vs.paletteEndianness = ep.getAttribute("endianness").equals("big")
						? ColorCodec.BIG_ENDIAN : ColorCodec.LITTLE_ENDIAN;
				vs.hasExternalPalette = true;
			} catch (NumberFormatException ignored) {}
		}

		return vs;
	}

	public String toXML() {
		StringBuilder sb = new StringBuilder();
		sb.append(" <viewsettings");
		if (codecID != null) sb.append(" codec=\"").append(escape(codecID)).append("\"");
		if (mode != null) sb.append(" mode=\"").append(mode == TileCodec.MODE_2D ? "2D" : "1D").append("\"");
		if (zoom != null) sb.append(" zoom=\"").append(zoom).append("\"");
		if (cols != null) sb.append(" cols=\"").append(cols).append("\"");
		if (rows != null) sb.append(" rows=\"").append(rows).append("\"");
		if (offset != null) sb.append(" offset=\"").append(offset).append("\"");

		if (hasExternalPalette) {
			sb.append(">\n");
			sb.append("  <externalpalette");
			sb.append(" path=\"").append(escape(palettePath)).append("\"");
			sb.append(" offset=\"").append(paletteOffset).append("\"");
			sb.append(" size=\"").append(paletteSize).append("\"");
			sb.append(" codec=\"").append(escape(paletteCodecID)).append("\"");
			sb.append(" endianness=\"")
					.append(paletteEndianness == ColorCodec.BIG_ENDIAN ? "big" : "little")
					.append("\"/>\n");
			sb.append(" </viewsettings>\n");
		} else {
			sb.append("/>\n");
		}
		return sb.toString();
	}

	public void captureFrom(TMView view) {
		TileCodec tc = view.getTileCodec();
		if (tc != null) codecID = tc.getID();
		mode = view.getMode();
		zoom = view.getScale();
		cols = view.getCols();
		rows = view.getRows();
		offset = view.getOffset();
	}

	public void applyTo(TMView view, TMUI ui) {
		if (codecID != null) {
			TileCodec tc = ui.getTileCodecByID(codecID);
			if (tc != null) view.setTileCodec(tc);
		}
		if (mode != null) view.setMode(mode);
		if (cols != null && rows != null) view.setGridSize(cols, rows);
		if (zoom != null) view.setScale(zoom);
		if (hasExternalPalette) applyExternalPalette(view, ui);
		// offset must be applied last: setGridSize/setScale recompute maxOffset
		if (offset != null) view.setAbsoluteOffset(offset);
	}

	private void applyExternalPalette(TMView view, TMUI ui) {
		File file = new File(palettePath);
		if (!file.exists()) return;
		ColorCodec codec = ui.getColorCodecByID(paletteCodecID);
		if (codec == null) return;

		byte[] data = new byte[paletteSize * codec.getBytesPerPixel()];
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			raf.seek(paletteOffset);
			raf.read(data);
		} catch (Exception ex) {
			return;
		}

		TMPalette palette = new TMPalette("ID", data, 0, paletteSize, codec, paletteEndianness, true, false);
		view.setPalette(palette);
	}

	public void setExternalPalette(String path, int offset, int size, String codecID, int endianness) {
		this.palettePath = path;
		this.paletteOffset = offset;
		this.paletteSize = size;
		this.paletteCodecID = codecID;
		this.paletteEndianness = endianness;
		this.hasExternalPalette = true;
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;")
				.replace("\"", "&quot;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}
}
